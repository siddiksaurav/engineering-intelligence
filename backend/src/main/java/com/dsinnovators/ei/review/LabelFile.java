package com.dsinnovators.ei.review;

import java.util.List;
import java.util.Locale;

/**
 * The rows of an imported label file, read by the columns {@code item_id}, {@code complexity} and
 * {@code note} of its header. Other columns are ignored, so the exported file can come back as it was.
 */
record LabelFile(List<Row> rows) {

    /** The cells of one row, stripped; a missing cell is empty. */
    record Row(int line, String itemId, String complexity, String note) {}

    private static final char BYTE_ORDER_MARK = '﻿';

    static LabelFile parse(String text) {
        String withoutMark = text.isEmpty() || text.charAt(0) != BYTE_ORDER_MARK ? text : text.substring(1);
        List<CsvReader.Row> csvRows = csvRowsOf(withoutMark);
        if (csvRows.isEmpty()) {
            throw refused("The file is empty; it needs a header with item_id, complexity and note");
        }
        List<String> header = csvRows.getFirst().cells().stream()
                .map(cell -> cell.strip().toLowerCase(Locale.ROOT))
                .toList();
        int itemIdColumn = header.indexOf("item_id");
        int complexityColumn = header.indexOf("complexity");
        int noteColumn = header.indexOf("note");
        if (itemIdColumn < 0 || complexityColumn < 0 || noteColumn < 0) {
            throw refused("The header must hold item_id, complexity and note, separated by ',' or ';'; found: "
                    + firstLine(withoutMark));
        }
        List<Row> rows = csvRows.stream()
                .skip(1)
                .map(row -> new Row(
                        row.line(), cellOf(row, itemIdColumn), cellOf(row, complexityColumn), cellOf(row, noteColumn)))
                .toList();
        return new LabelFile(rows);
    }

    private static List<CsvReader.Row> csvRowsOf(String text) {
        try {
            return CsvReader.read(text, separatorOf(text));
        } catch (IllegalArgumentException unclosedQuote) {
            throw refused(unclosedQuote.getMessage());
        }
    }

    private static String cellOf(CsvReader.Row row, int column) {
        return column < row.cells().size() ? row.cells().get(column).strip() : "";
    }

    /** The separator of the header line: a spreadsheet may save ';' where the export has ','. */
    private static char separatorOf(String text) {
        String header = firstLine(text);
        return header.contains(",") || !header.contains(";") ? ',' : ';';
    }

    private static String firstLine(String text) {
        int lineEnd = text.indexOf('\n');
        return (lineEnd < 0 ? text : text.substring(0, lineEnd)).strip();
    }

    private static LabelImportRefusedException refused(String message) {
        return new LabelImportRefusedException(message, List.of());
    }
}
