package com.ldbc.driver.runtime;

import com.ldbc.driver.*;
import com.ldbc.driver.control.ConcurrentControlService;
import com.ldbc.driver.runtime.coordination.ConcurrentCompletionTimeService;
import com.ldbc.driver.runtime.coordination.ThreadedQueuedConcurrentCompletionTimeService;
import com.ldbc.driver.runtime.executor.*;
import com.ldbc.driver.runtime.metrics.ConcurrentMetricsService;
import com.ldbc.driver.runtime.scheduling.ErrorReportingExecutionDelayPolicy;
import com.ldbc.driver.runtime.scheduling.ExecutionDelayPolicy;
import com.ldbc.driver.runtime.scheduling.Spinner;
import com.ldbc.driver.runtime.streams.IteratorSplitter;
import com.ldbc.driver.runtime.streams.IteratorSplittingException;
import com.ldbc.driver.runtime.streams.SplitDefinition;
import com.ldbc.driver.runtime.streams.SplitResult;
import com.ldbc.driver.temporal.Duration;
import com.ldbc.driver.temporal.Time;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkloadRunner {
    private final Duration DEFAULT_STATUS_UPDATE_INTERVAL = Duration.fromSeconds(2);
    private final Duration SPINNER_OFFSET_DURATION = Duration.fromMilli(100);

    private final Spinner exactSpinner;
    private final Spinner earlySpinner;

    // TODO make service and inject into workload runner
    private final WorkloadStatusThread workloadStatusThread;

    private final ConcurrentControlService controlService;
    private final ConcurrentCompletionTimeService completionTimeService;
    private final ConcurrentErrorReporter errorReporter;

    private final OperationHandlerExecutor operationHandlerExecutor;

    private final PreciseIndividualAsyncOperationStreamExecutorService preciseIndividualAsyncOperationStreamExecutorService;
    private final PreciseIndividualBlockingOperationStreamExecutorService preciseIndividualBlockingOperationStreamExecutorService;
    private final UniformWindowedOperationStreamExecutorService uniformWindowedOperationStreamExecutorService;

    public WorkloadRunner(ConcurrentControlService controlService,
                          Db db,
                          Iterator<Operation<?>> operations,
                          Map<Class<? extends Operation<?>>, OperationClassification> operationClassifications,
                          ConcurrentMetricsService metricsService,
                          ConcurrentErrorReporter errorReporter) throws WorkloadException {
        this.controlService = controlService;
        this.errorReporter = errorReporter;

        ExecutionDelayPolicy executionDelayPolicy = new ErrorReportingExecutionDelayPolicy(controlService.configuration().toleratedExecutionDelay(), errorReporter);

        // TODO for the spinner sent to Window scheduler allow delay to reach to the end of window?

        this.exactSpinner = new Spinner(executionDelayPolicy);
        this.earlySpinner = new Spinner(executionDelayPolicy, SPINNER_OFFSET_DURATION);
        this.workloadStatusThread = new WorkloadStatusThread(DEFAULT_STATUS_UPDATE_INTERVAL, metricsService, errorReporter);

        // Create GCT maintenance thread
        try {
            completionTimeService = new ThreadedQueuedConcurrentCompletionTimeService(controlService.configuration().peerIds(), errorReporter);
        } catch (Exception e) {
            throw new WorkloadException(
                    String.format("Error while instantiating Completion Time Service with peer IDs %s",
                            controlService.configuration().peerIds().toString()),
                    e.getCause());
        }

        // Set GCT to just before scheduled start time of earliest operation in process's stream
        try {
            completionTimeService.submitInitiatedTime(controlService.workloadStartTime());
            completionTimeService.submitCompletedTime(controlService.workloadStartTime());
            for (String peerId : controlService.configuration().peerIds()) {
                completionTimeService.submitExternalCompletionTime(peerId, controlService.workloadStartTime());
            }
            // Wait for workloadStartTime to be applied
            Future<Time> globalCompletionTimeFuture = completionTimeService.globalCompletionTimeFuture();
            while (false == globalCompletionTimeFuture.isDone()) {
                if (errorReporter.errorEncountered())
                    throw new WorkloadException(String.format("Encountered error while waiting for GCT to initialize. Driver terminating.\n%s", errorReporter.toString()));
            }
            if (false == globalCompletionTimeFuture.get().equals(controlService.workloadStartTime())) {
                throw new WorkloadException("Completion Time future failed to return expected value");
            }
        } catch (WorkloadException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkloadException("Error while read/writing Completion Time Service", e.getCause());
        }

        Iterator<Operation<?>> windowedOperations;
        Iterator<Operation<?>> blockingOperations;
        Iterator<Operation<?>> asynchronousOperations;
        try {
            IteratorSplitter<Operation<?>> splitter = new IteratorSplitter<Operation<?>>(IteratorSplitter.UnmappedItemPolicy.ABORT);
            SplitDefinition<Operation<?>> windowed = new SplitDefinition<Operation<?>>(Workload.operationTypesBySchedulingMode(operationClassifications, OperationClassification.SchedulingMode.WINDOWED));
            SplitDefinition<Operation<?>> blocking = new SplitDefinition<Operation<?>>(Workload.operationTypesBySchedulingMode(operationClassifications, OperationClassification.SchedulingMode.INDIVIDUAL_BLOCKING));
            SplitDefinition<Operation<?>> asynchronous = new SplitDefinition<Operation<?>>(Workload.operationTypesBySchedulingMode(operationClassifications, OperationClassification.SchedulingMode.INDIVIDUAL_ASYNC));
            SplitResult splits = splitter.split(operations, windowed, blocking, asynchronous);
            windowedOperations = splits.getSplitFor(windowed).iterator();
            blockingOperations = splits.getSplitFor(blocking).iterator();
            asynchronousOperations = splits.getSplitFor(asynchronous).iterator();
        } catch (IteratorSplittingException e) {
            throw new WorkloadException(
                    String.format("Error while splitting operation stream by scheduling mode\n%s", ConcurrentErrorReporter.stackTraceToString(e)),
                    e.getCause());
        }

        OperationsToHandlersTransformer operationsToHandlersTransformer = new OperationsToHandlersTransformer(
                db,
                exactSpinner,
                completionTimeService,
                errorReporter,
                metricsService,
                controlService.configuration().gctDeltaDuration(),
                operationClassifications);
        Iterator<OperationHandler<?>> windowedHandlers = operationsToHandlersTransformer.transform(windowedOperations);
        Iterator<OperationHandler<?>> blockingHandlers = operationsToHandlersTransformer.transform(blockingOperations);
        Iterator<OperationHandler<?>> asynchronousHandlers = operationsToHandlersTransformer.transform(asynchronousOperations);

        // TODO these executor services should all be using different gct services and sharing gct via external ct [MUST]
        // This lesson needs to be written to Confluence too

        this.operationHandlerExecutor = new ThreadPoolOperationHandlerExecutor(controlService.configuration().threadCount());
        this.preciseIndividualAsyncOperationStreamExecutorService = new PreciseIndividualAsyncOperationStreamExecutorService(
                errorReporter, completionTimeService, asynchronousHandlers, earlySpinner, operationHandlerExecutor);
        this.preciseIndividualBlockingOperationStreamExecutorService = new PreciseIndividualBlockingOperationStreamExecutorService(
                errorReporter, completionTimeService, blockingHandlers, earlySpinner, operationHandlerExecutor);
        // TODO better way of setting window size. it does not need to equal DeltaT, it can be smaller. where to set? how to set?
        Duration windowSize = controlService.configuration().gctDeltaDuration();
        this.uniformWindowedOperationStreamExecutorService = new UniformWindowedOperationStreamExecutorService(
                errorReporter, completionTimeService, windowedHandlers, operationHandlerExecutor, earlySpinner, controlService.workloadStartTime(), windowSize);
    }

    public void executeWorkload() throws WorkloadException {
        // TODO wait until control service start time, or just before, so status isn't being reported too early

        // TODO revise if this necessary here, and if not where??
        controlService.waitForCommandToExecuteWorkload();

        if (controlService.configuration().showStatus()) workloadStatusThread.start();
        AtomicBoolean asyncHandlersFinished = preciseIndividualAsyncOperationStreamExecutorService.execute();
        AtomicBoolean blockingHandlersFinished = preciseIndividualBlockingOperationStreamExecutorService.execute();
        AtomicBoolean windowedHandlersFinished = uniformWindowedOperationStreamExecutorService.execute();

        AtomicBoolean[] executorFinishedFlags = new AtomicBoolean[]{asyncHandlersFinished, blockingHandlersFinished, windowedHandlersFinished};
        while (true) {
            if (errorReporter.errorEncountered()) break;
            for (int i = 0; i < executorFinishedFlags.length; i++) {
                if (null != executorFinishedFlags[i] && executorFinishedFlags[i].get()) executorFinishedFlags[i] = null;
            }
            boolean terminate = true;
            for (int i = 0; i < executorFinishedFlags.length; i++) {
                if (null != executorFinishedFlags[i]) terminate = false;
            }
            if (terminate) break;
        }

        while (true) {
            if (errorReporter.errorEncountered())
                break;
            if (asyncHandlersFinished.get() && blockingHandlersFinished.get() && windowedHandlersFinished.get())
                break;
        }

        // TODO cleanup everything properly first? what needs to be cleaned up?
        if (errorReporter.errorEncountered()) {
            throw new WorkloadException(String.format("Encountered error while running workload. Driver terminating.\n%s", errorReporter.toString()));
        }

        // TODO should executors wait for all operations to terminate before returning?
        preciseIndividualAsyncOperationStreamExecutorService.shutdown();
        preciseIndividualBlockingOperationStreamExecutorService.shutdown();
        uniformWindowedOperationStreamExecutorService.shutdown();

        // TODO if multiple executors are used (different executors for different executor services) shut them all down here
        try {
            // TODO this is total bullshit, need a better way of doing this. should be handled by executor services already
            this.operationHandlerExecutor.shutdown(Duration.fromSeconds(3600));
        } catch (OperationHandlerExecutorException e) {
            throw new WorkloadException("Encountered error while shutting down operation handler executor", e);
        }

        // TODO make status reporting service. this could report to coordinator. it could also report to a local console printer.
        if (controlService.configuration().showStatus()) workloadStatusThread.interrupt();

        controlService.waitForAllToCompleteExecutingWorkload();
    }
}