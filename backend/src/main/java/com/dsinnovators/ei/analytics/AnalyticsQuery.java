package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.BotAccounts;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Starts an analytic's query with the parameters every one needs, and reads a count from a result row. */
@Component
class AnalyticsQuery {

    private final JdbcClient jdbc;
    private final BotAccounts botAccounts;
    private final JsonMapper jsonMapper;

    AnalyticsQuery(JdbcClient jdbc, BotAccounts botAccounts, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.botAccounts = botAccounts;
        this.jsonMapper = jsonMapper;
    }

    /** The statement with the first and last day of the quarter and the bot logins in force in it. */
    JdbcClient.StatementSpec statement(String sql, Quarter quarter) {
        return jdbc.sql(sql)
                .param("firstDay", quarter.firstDay())
                .param("lastDay", quarter.lastDay())
                .param("botLogins", jsonMapper.writeValueAsString(botAccounts.loginsOfBots(quarter)));
    }

    /** The counts of the rows, by the text in {@code bucketColumn}, in the order of the rows. */
    static Map<String, Count> countsByBucket(JdbcClient.StatementSpec statement, String bucketColumn) {
        Map<String, Count> countsByBucket = new LinkedHashMap<>();
        statement
                .query((row, rowNumber) -> countsByBucket.put(row.getString(bucketColumn), countOf(row)))
                .list();
        return countsByBucket;
    }

    /** The count in the columns {@code count}, {@code item_ids} and {@code pull_request_numbers} of the current row. */
    static Count countOf(ResultSet row) throws SQLException {
        return new Count(
                row.getLong("count"),
                numbers(row.getArray("item_ids")).stream()
                        .map(Number::longValue)
                        .toList(),
                numbers(row.getArray("pull_request_numbers")).stream()
                        .map(Number::intValue)
                        .toList());
    }

    private static List<Number> numbers(Array column) throws SQLException {
        List<Number> numbers = new ArrayList<>();
        if (column != null) {
            for (Object number : (Object[]) column.getArray()) {
                numbers.add((Number) number);
            }
        }
        return numbers;
    }
}
