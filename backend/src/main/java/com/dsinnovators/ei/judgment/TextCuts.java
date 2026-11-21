package com.dsinnovators.ei.judgment;

/** Cuts a text that is longer than its budget and says so, so the model knows it does not see all of it. */
final class TextCuts {

    private TextCuts() {}

    /** The text itself when it fits, else its first {@code maxCharacters} characters and a line that names the cut. */
    static String cut(String text, int maxCharacters) {
        if (text.length() <= maxCharacters) {
            return text;
        }
        int end = maxCharacters;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "\n[description cut at " + maxCharacters + " characters]";
    }
}
