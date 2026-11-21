package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.github.PullRequestDiff;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the model reads of one pull request: title, description, the file list with path and status
 * only (decision 132), and whole patches added in the order of the selection until the diff budget is
 * used (decision 133). Whatever is not sent is named, with the reason.
 *
 * @param text the user message sent to the model
 * @param hash SHA-256 over the kind, the prompt version, the text and the source record ids; the same
 *     hash is never judged twice (decision 157)
 * @param judgedOnPartialCode true when a patch was left out for size; set by the system, not the model
 */
record DigestInput(String text, String hash, boolean judgedOnPartialCode) {

    /**
     * Builds the input.
     *
     * @param pullRequestRecordId the exact pull request record version
     * @param selection what {@link DiffFileFilter} allows to send
     */
    static DigestInput of(
            long pullRequestRecordId,
            String title,
            String body,
            PullRequestDiff diff,
            DiffFileFilter.Selection selection,
            DigestLimits limits,
            String promptVersion) {
        StringBuilder text = new StringBuilder();
        text.append("Title: ").append(title).append("\n\nDescription:\n");
        text.append(cut(body, limits.maxBodyCharacters())).append("\n\nChanged files:\n");
        diff.files()
                .forEach(file -> text.append("- ")
                        .append(file.path())
                        .append(" (")
                        .append(file.status())
                        .append(")\n"));

        List<String> notSent = new ArrayList<>();
        selection.excludedPaths().forEach(path -> notSent.add(path + ": excluded (generated, vendored or translated)"));
        diff.files().stream()
                .filter(file -> file.patch().isEmpty())
                .forEach(file -> notSent.add(file.path() + ": no patch from GitHub"));

        text.append("\nPatches:\n");
        int used = 0;
        boolean partial = false;
        for (PullRequestDiff.File file : selection.patchCandidates()) {
            String patch = file.patch().orElseThrow();
            if (used + patch.length() > limits.maxDiffCharacters()) {
                notSent.add(file.path() + ": over the size budget");
                partial = true;
                continue;
            }
            used += patch.length();
            text.append("=== ")
                    .append(file.path())
                    .append(" ===\n")
                    .append(patch)
                    .append('\n');
        }
        if (!notSent.isEmpty()) {
            text.append("\nPatches not sent:\n");
            notSent.forEach(line -> text.append("- ").append(line).append('\n'));
        }
        String input = text.toString();
        return new DigestInput(input, hashOf(input, promptVersion, pullRequestRecordId, diff.recordId()), partial);
    }

    private static String cut(String body, int maxCharacters) {
        if (body.length() <= maxCharacters) {
            return body;
        }
        int end = maxCharacters;
        if (Character.isHighSurrogate(body.charAt(end - 1))) {
            end--;
        }
        return body.substring(0, end) + "\n[description cut at " + maxCharacters + " characters]";
    }

    private static String hashOf(String input, String promptVersion, long... sourceRecordIds) {
        ObjectNode hashed = JsonNodeFactory.instance.objectNode();
        hashed.put("kind", JudgmentKind.PULL_REQUEST_DIGEST.name());
        hashed.put("promptVersion", promptVersion);
        hashed.put("input", input);
        ArrayNode records = hashed.putArray("sourceRecordIds");
        for (long id : sourceRecordIds) {
            records.add(id);
        }
        return CanonicalJson.sha256Hex(hashed);
    }
}
