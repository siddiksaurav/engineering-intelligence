package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.RawRecordStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Stores a fetched work package as raw records, one per part, in one transaction: a work package is
 * stored whole or not at all.
 */
@Component
class WorkPackageRecorder {

    private final RawRecordStore store;
    private final OpenProjectSource source;

    WorkPackageRecorder(RawRecordStore store, OpenProjectSource source) {
        this.store = store;
        this.source = source;
    }

    /**
     * Stores the work package, then each activity, then the pull request list, and says what each
     * store did.
     *
     * @throws OpenProjectApiException when an activity has no id; nothing is stored
     */
    @Transactional
    List<StoredRecord> store(FetchedWorkPackage fetched) {
        String number = String.valueOf(fetched.number());
        String url = source.dataSource().getBaseUrl() + "/work_packages/" + number;
        List<PendingRecord> records = new ArrayList<>();
        records.add(new PendingRecord(OpenProjectRecordKind.WORK_PACKAGE, number, url, fetched.workPackage()));
        for (JsonNode activity : fetched.activities().path("_embedded").path("elements")) {
            records.add(new PendingRecord(
                    OpenProjectRecordKind.WORK_PACKAGE_ACTIVITY,
                    activityId(activity, number),
                    url + "/activity",
                    activity));
        }
        records.add(new PendingRecord(
                OpenProjectRecordKind.WORK_PACKAGE_PULL_REQUESTS,
                number,
                url + "/tabs/github",
                fetched.pullRequestList()));
        return records.stream().map(this::store).toList();
    }

    private StoredRecord store(PendingRecord record) {
        return new StoredRecord(
                record.kind(),
                store.store(
                                OpenProjectSource.KEY,
                                record.kind().recordKind(),
                                record.externalId(),
                                record.url(),
                                record.payload())
                        .outcome());
    }

    private static String activityId(JsonNode activity, String number) {
        JsonNode id = activity.path("id");
        if (!id.isIntegralNumber()) {
            throw new OpenProjectApiException("An activity of work package " + number + " has no id: " + activity);
        }
        return id.asString();
    }

    private record PendingRecord(OpenProjectRecordKind kind, String externalId, String url, JsonNode payload) {}
}
