package com.dsinnovators.ei.api;

import java.util.List;
import java.util.stream.Collectors;

/** Writes rows of text as CSV by RFC 4180: comma-separated, a row ends with CRLF, a cell with a comma, a quote or a line break is quoted. */
final class CsvWriter {

    private static final String ROW_END = "\r\n";

    private final StringBuilder text = new StringBuilder();

    CsvWriter row(List<String> cells) {
        text.append(cells.stream().map(CsvWriter::cell).collect(Collectors.joining(",")))
                .append(ROW_END);
        return this;
    }

    String text() {
        return text.toString();
    }

    private static String cell(String value) {
        boolean needsQuotes =
                value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
