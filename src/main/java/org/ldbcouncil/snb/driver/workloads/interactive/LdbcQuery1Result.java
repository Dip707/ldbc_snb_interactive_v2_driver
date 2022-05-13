package org.ldbcouncil.snb.driver.workloads.interactive;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import org.ldbcouncil.snb.driver.util.ListUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LdbcQuery1Result {
    private final long friendId;
    private final String friendLastName;
    private final int distanceFromPerson;
    private final long friendBirthday;
    private final long friendCreationDate;
    private final String friendGender;
    private final String friendBrowserUsed;
    private final String friendLocationIp;
    private final Iterable<String> friendEmails;
    private final Iterable<String> friendLanguages;
    private final String friendCityName;
    // (Person-studyAt->University.name,
    // Person-studyAt->.classYear,
    // Person-studyAt->University-isLocatedIn->City.name)
    private final Iterable<List<Object>> friendUniversities;
    // (Person-workAt->Company.name,
    // Person-workAt->.workFrom,
    // Person-workAt->Company-isLocatedIn->City.name)
    private final Iterable<List<Object>> friendCompanies;

    public LdbcQuery1Result(
        @JsonProperty("friendId") long friendId,
        @JsonProperty("friendLastName")     String friendLastName,
        @JsonProperty("distanceFromPerson")    int distanceFromPerson,
        @JsonProperty("friendBirthday")     long friendBirthday,
        @JsonProperty("friendCreationDate")    long friendCreationDate,
        @JsonProperty("friendGender")    String friendGender,
        @JsonProperty("friendBrowserUsed")   String friendBrowserUsed,
        @JsonProperty("friendLocationIp")   String friendLocationIp,
        @JsonProperty("friendEmails")   Iterable<String> friendEmails,
        @JsonProperty("friendLanguages")   Iterable<String> friendLanguages,
        @JsonProperty("friendCityName")    String friendCityName,
        @JsonProperty("friendUniversities")   Iterable<List<Object>> friendUniversities,
        @JsonProperty("friendCompanies")   Iterable<List<Object>> friendCompanies) {
        this.friendId = friendId;
        this.friendLastName = friendLastName;
        this.distanceFromPerson = distanceFromPerson;
        this.friendBirthday = friendBirthday;
        this.friendCreationDate = friendCreationDate;
        this.friendGender = friendGender;
        this.friendBrowserUsed = friendBrowserUsed;
        this.friendLocationIp = friendLocationIp;
        this.friendEmails = friendEmails;
        this.friendLanguages = friendLanguages;
        this.friendCityName = friendCityName;
        this.friendUniversities = friendUniversities;
        this.friendCompanies = friendCompanies;
    }

    public long getFriendId() {
        return friendId;
    }

    public String getFriendLastName() {
        return friendLastName;
    }

    public int getDistanceFromPerson() {
        return distanceFromPerson;
    }

    public long getFriendBirthday() {
        return friendBirthday;
    }

    public long getFriendCreationDate() {
        return friendCreationDate;
    }

    public String getFriendGender() {
        return friendGender;
    }

    public String getFriendBrowserUsed() {
        return friendBrowserUsed;
    }

    public String getFriendLocationIp() {
        return friendLocationIp;
    }

    public Iterable<String> getFriendEmails() {
        return friendEmails;
    }

    public Iterable<String> getFriendLanguages() {
        return friendLanguages;
    }

    public String getFriendCityName() {
        return friendCityName;
    }

    public Iterable<List<Object>> getFriendUniversities() {
        return friendUniversities;
    }

    public Iterable<List<Object>> getFriendCompanies() {
        return friendCompanies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LdbcQuery1Result other = (LdbcQuery1Result) o;

        if (distanceFromPerson != other.distanceFromPerson) return false;
        if (friendBirthday != other.friendBirthday) return false;
        if (friendCreationDate != other.friendCreationDate) return false;
        if (friendId != other.friendId) return false;
        if (friendBrowserUsed != null ? !friendBrowserUsed.equals(other.friendBrowserUsed) : other.friendBrowserUsed != null)
            return false;
        if (friendCityName != null ? !friendCityName.equals(other.friendCityName) : other.friendCityName != null)
            return false;
        if ((null == friendCompanies ^ null == other.friendCompanies) || false == ListUtils.listsOfListsEqual(sortListOfObjectLists(friendCompanies), sortListOfObjectLists(other.friendCompanies)))
            return false;
        if (friendEmails != null ? !ListUtils.listsEqual(sortStringList(friendEmails), sortStringList(other.friendEmails)) : other.friendEmails != null)
            return false;
        if (friendGender != null ? !friendGender.equals(other.friendGender) : other.friendGender != null)
            return false;
        if (friendLanguages != null ? !ListUtils.listsEqual(sortStringList(friendLanguages), sortStringList(other.friendLanguages)) : other.friendLanguages != null)
            return false;
        if (friendLastName != null ? !friendLastName.equals(other.friendLastName) : other.friendLastName != null)
            return false;
        if (friendLocationIp != null ? !friendLocationIp.equals(other.friendLocationIp) : other.friendLocationIp != null)
            return false;
        if (friendUniversities != null ? !ListUtils.listsOfListsEqual(sortListOfObjectLists(friendUniversities), sortListOfObjectLists(other.friendUniversities)) : other.friendUniversities != null)
            return false;
        return true;
    }

    private List<String> sortStringList(Iterable<String> iterable) {
        List<String> list = Lists.newArrayList(iterable);
        Collections.sort(list);
        return list;
    }

    private static final Comparator<List<Object>> threeElementStringListComparator = new Comparator<List<Object>>() {
        @Override
        public int compare(List<Object> o1, List<Object> o2) {
            if (o1.size() != 3)
                throw new RuntimeException("List must contain exactly three elements: " + o1);
            if (o2.size() != 3)
                throw new RuntimeException("List must contain exactly three elements: " + o1);
            int result = o1.get(0).toString().compareTo(o2.get(0).toString());
            if (0 != result) return result;
            result = o1.get(1).toString().compareTo(o2.get(1).toString());
            if (0 != result) return result;
            return o1.get(2).toString().compareTo(o2.get(2).toString());
        }
    };

    private List<List<Object>> sortListOfObjectLists(Iterable<List<Object>> iterable) {
        List<List<Object>> list = Lists.newArrayList(iterable);
        Collections.sort(list, threeElementStringListComparator);
        return list;
    }

    @Override
    public String toString() {
        return "LdbcQuery1Result{" +
                "friendId=" + friendId +
                ", friendLastName='" + friendLastName + '\'' +
                ", distanceFromPerson=" + distanceFromPerson +
                ", friendBirthday=" + friendBirthday +
                ", friendCreationDate=" + friendCreationDate +
                ", friendGender='" + friendGender + '\'' +
                ", friendBrowserUsed='" + friendBrowserUsed + '\'' +
                ", friendLocationIp='" + friendLocationIp + '\'' +
                ", friendEmails=" + friendEmails +
                ", friendLanguages=" + friendLanguages +
                ", friendCityName='" + friendCityName + '\'' +
                ", friendUniversities=" + friendUniversities +
                ", friendCompanies=" + friendCompanies +
                '}';
    }
}
