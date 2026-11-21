package com.dsinnovators.ei.judgment;

/** Cuts a text that is longer than its budget and says so, so the model knows it does not see all of it. */
final class TextCuts {

    private TextCuts() {}

    /** The text itself when it fits, else its first {@code maxCharacters} characters and a line that names the cut. */
    static String cut(String text, int maxCharacters) {
        return cut(text, maxCharacters, "description");
    }

    /** As {@link #cut(String, int)}, the line naming {@code subject}, such as {@code comment}. */
    static String cut(String text, int maxCharacters, String subject) {
        if (text.length() <= maxCharacters) {
            return text;
        }
        int end = maxCharacters;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "\n[" + subject + " cut at " + maxCharacters + " characters]";
    }

    /**
     * The text itself when it fits, else a line that names the cut and its last {@code maxCharacters}
     * characters: for a diff hunk, whose commented line is the last.
     */
    static String tail(String text, int maxCharacters, String subject) {
        if (text.length() <= maxCharacters) {
            return text;
        }
        int start = text.length() - maxCharacters;
        if (start < text.length() && Character.isLowSurrogate(text.charAt(start))) {
            start++;
        }
        return "[" + subject + " cut: only the last " + maxCharacters + " characters are shown]\n"
                + text.substring(start);
    }
}
