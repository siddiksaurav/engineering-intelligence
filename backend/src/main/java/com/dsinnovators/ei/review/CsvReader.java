package com.dsinnovators.ei.review;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads CSV text by RFC 4180 with a chosen separator: a cell in quotes may hold the separator, a line
 * break and a doubled quote. Rows that hold nothing at all are left out.
 */
final class CsvReader {

    /** One row of the file, with the number of the line it starts on, counted from 1. */
    record Row(int line, List<String> cells) {}

    private final String text;
    private final char separator;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> cells = new ArrayList<>();
    private final StringBuilder cell = new StringBuilder();
    private int line = 1;
    private int rowStartLine = 1;
    private boolean quoted;

    private CsvReader(String text, char separator) {
        this.text = text;
        this.separator = separator;
    }

    static List<Row> read(String text, char separator) {
        return new CsvReader(text, separator).readRows();
    }

    private List<Row> readRows() {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                index += readInsideQuotes(character, index);
            } else if (character == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (character == separator) {
                endCell();
            } else if (character == '\r' || character == '\n') {
                index += endRow(character, index);
            } else {
                cell.append(character);
            }
        }
        endRow();
        return rows;
    }

    /** Returns how many extra characters were used. */
    private int readInsideQuotes(char character, int index) {
        if (character != '"') {
            cell.append(character);
            if (character == '\n') {
                line++;
            }
            return 0;
        }
        if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
            cell.append('"');
            return 1;
        }
        quoted = false;
        return 0;
    }

    /** Returns how many extra characters were used: one when a CRLF ends the row. */
    private int endRow(char character, int index) {
        endRow();
        line++;
        rowStartLine = line;
        return character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n' ? 1 : 0;
    }

    private void endCell() {
        cells.add(cell.toString());
        cell.setLength(0);
    }

    private void endRow() {
        endCell();
        boolean empty = cells.stream().allMatch(String::isEmpty);
        if (!empty) {
            rows.add(new Row(rowStartLine, List.copyOf(cells)));
        }
        cells.clear();
    }
}
