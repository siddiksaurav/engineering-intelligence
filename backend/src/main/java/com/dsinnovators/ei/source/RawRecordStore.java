package com.dsinnovators.ei.source;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** The only way to write a raw record, and the way to read one. */
public interface RawRecordStore {

    /**
     * Stores a copy of a pulled record if its content differs from the latest stored version.
     *
     * @throws IllegalArgumentException for an unknown data source key or a missing argument
     */
    StoreResult store(String dataSourceKey, String recordKind, String externalId, String url, JsonNode payload);

    Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId);

    /** All versions of one record, oldest first. */
    List<RawRecord> findVersions(String dataSourceKey, String recordKind, String externalId);

    /** The current copy of each record of one kind. */
    List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind);
}
