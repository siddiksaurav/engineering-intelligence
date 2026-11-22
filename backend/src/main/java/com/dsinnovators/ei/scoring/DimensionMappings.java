package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads the dimensions with their evidence types and gates in force for a quarter: per dimension,
 * the rows with the latest {@code valid_from_quarter} that is not after the quarter (the rule of
 * {@code setting}, decision 24).
 */
@Component
public class DimensionMappings {

    private static final String DIMENSIONS = "select id, key, name from dimension order by id";

    private static final String EVIDENCE_TYPES = """
            select evidence_type, tiered, from_level, valid_from_quarter
            from dimension_evidence_type
            where dimension_id = :dimensionId
              and valid_from_quarter = (select max(valid_from_quarter)
                                        from dimension_evidence_type
                                        where dimension_id = :dimensionId and valid_from_quarter <= :quarter)
            order by evidence_type
            """;

    private static final String GATES = """
            select gate, applied, valid_from_quarter
            from dimension_gate
            where dimension_id = :dimensionId
              and valid_from_quarter = (select max(valid_from_quarter)
                                        from dimension_gate
                                        where dimension_id = :dimensionId and valid_from_quarter <= :quarter)
            order by gate
            """;

    private record Versioned<T>(T item, Quarter version) {}

    private final JdbcClient jdbc;

    DimensionMappings(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The dimensions in the order of their ids; fails when a dimension has no evidence type in force. */
    public List<DimensionMapping> inForce(Quarter quarter) {
        return jdbc.sql(DIMENSIONS)
                .query((row, rowNumber) ->
                        mappingOf(row.getLong("id"), row.getString("key"), row.getString("name"), quarter))
                .list();
    }

    private DimensionMapping mappingOf(long dimensionId, String key, String name, Quarter quarter) {
        List<Versioned<MappedEvidenceType>> evidenceTypes = jdbc.sql(EVIDENCE_TYPES)
                .param("dimensionId", dimensionId)
                .param("quarter", quarter.toString())
                .query((row, rowNumber) -> new Versioned<>(
                        new MappedEvidenceType(
                                EvidenceType.valueOf(
                                        row.getString("evidence_type").toUpperCase(Locale.ROOT)),
                                row.getBoolean("tiered"),
                                Level.valueOf(row.getString("from_level"))),
                        Quarter.parse(row.getString("valid_from_quarter"))))
                .list();
        if (evidenceTypes.isEmpty()) {
            throw new IllegalStateException(
                    "Dimension '" + key + "' has no evidence type mapping in force in " + quarter);
        }
        List<Versioned<MappedGate>> gates = jdbc.sql(GATES)
                .param("dimensionId", dimensionId)
                .param("quarter", quarter.toString())
                .query((row, rowNumber) -> new Versioned<>(
                        new MappedGate(Gate.valueOf(row.getString("gate")), row.getBoolean("applied")),
                        Quarter.parse(row.getString("valid_from_quarter"))))
                .list();
        return new DimensionMapping(
                dimensionId,
                key,
                name,
                evidenceTypes.getFirst().version(),
                evidenceTypes.stream().map(Versioned::item).toList(),
                gates.stream().findFirst().map(Versioned::version),
                gates.stream().map(Versioned::item).toList());
    }
}
