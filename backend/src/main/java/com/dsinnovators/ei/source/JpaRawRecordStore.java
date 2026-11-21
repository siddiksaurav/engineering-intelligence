package com.dsinnovators.ei.source;

import com.dsinnovators.ei.common.CanonicalJson;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
class JpaRawRecordStore implements RawRecordStore {

    private final DataSourceRepository dataSources;
    private final RawRecordRepository rawRecords;

    JpaRawRecordStore(DataSourceRepository dataSources, RawRecordRepository rawRecords) {
        this.dataSources = dataSources;
        this.rawRecords = rawRecords;
    }

    @Override
    @Transactional
    public StoreResult store(String dataSourceKey, String recordKind, String externalId, String url, JsonNode payload) {
        requireText(recordKind, "recordKind");
        requireText(externalId, "externalId");
        requireText(url, "url");
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        DataSource dataSource = dataSource(dataSourceKey);
        NulCharacters.Escaped escaped = NulCharacters.escape(payload);
        String contentHash = CanonicalJson.sha256Hex(escaped.payload());

        Optional<RawRecord> latest = rawRecords.findLatestVersion(dataSource, recordKind, externalId);
        if (latest.isPresent() && latest.get().getContentHash().equals(contentHash)) {
            return new StoreResult(StoreResult.Outcome.UNCHANGED, latest.get());
        }
        int version = latest.map(record -> record.getVersion() + 1).orElse(1);
        RawRecord written = rawRecords.save(new RawRecord(
                dataSource,
                recordKind,
                externalId,
                version,
                url,
                contentHash,
                escaped.payload(),
                escaped.anyEscaped(),
                Instant.now()));
        return new StoreResult(version == 1 ? StoreResult.Outcome.CREATED : StoreResult.Outcome.NEW_VERSION, written);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId) {
        requireText(recordKind, "recordKind");
        requireText(externalId, "externalId");
        return rawRecords.findLatestVersion(dataSource(dataSourceKey), recordKind, externalId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RawRecord> findById(long id) {
        return rawRecords.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawRecord> findVersions(String dataSourceKey, String recordKind, String externalId) {
        requireText(recordKind, "recordKind");
        requireText(externalId, "externalId");
        return rawRecords.findVersions(dataSource(dataSourceKey), recordKind, externalId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind) {
        requireText(recordKind, "recordKind");
        return rawRecords.findLatestByKind(dataSource(dataSourceKey), recordKind);
    }

    private DataSource dataSource(String key) {
        requireText(key, "dataSourceKey");
        return dataSources
                .findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown data source: " + key));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
