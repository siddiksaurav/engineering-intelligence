package com.dsinnovators.ei.source;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** The only way to write a raw record, and the way to read one. */
public interface RawRecordStore {

    /**
     * Stores a copy of a pulled record if its content differs from the latest stored version. Any
     * character U+0000 is stored as the text {@code \u0000} and the row is marked (see {@link
     * RawRecord#isNulEscaped()}); the content hash is taken over the stored form.
     *
     * @throws IllegalArgumentException for an unknown data source key or a missing argument
     */
    StoreResult store(String dataSourceKey, String recordKind, String externalId, String url, JsonNode payload);

    Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId);

    /** The exact version of a record that has this id, whether or not it is the latest. */
    Optional<RawRecord> findById(long id);

    /** All versions of one record, oldest first. */
    List<RawRecord> findVersions(String dataSourceKey, String recordKind, String externalId);

    /** The current copy of each record of one kind. */
    List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind);
}
