package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the work items a pull request names: OpenProject URLs in the body and a bracketed key in
 * the title. Branch names are not read, they give false matches (decision 57).
 */
public final class WorkPackageReferenceFinder {

    private static final String KEY_OR_NUMBER = "[A-Za-z][A-Za-z0-9]*-[0-9]+|[0-9]+";
    private static final Pattern BRACKETED_KEY = Pattern.compile("\\[([A-Za-z][A-Za-z0-9]*-[0-9]+)]");

    private final Pattern bodyUrl;

    /**
     * @param baseUrl the OpenProject site, taken from the {@code openproject} data source
     */
    public WorkPackageReferenceFinder(URI baseUrl) {
        String host = baseUrl.getHost();
        if (host == null) {
            throw new IllegalArgumentException("The OpenProject base URL has no host: " + baseUrl);
        }
        this.bodyUrl = Pattern.compile("https?://" + Pattern.quote(host)
                + "/(?:wp|work_packages|projects/[^/\\s]+/work_packages)/(" + KEY_OR_NUMBER + ")(?![A-Za-z0-9-])");
    }

    /** The distinct references of a pull request, body first, each with where it was found. */
    public List<WorkPackageReference> find(String title, String body) {
        Set<WorkPackageReference> references = new LinkedHashSet<>();
        collect(bodyUrl.matcher(body), FoundIn.PULL_REQUEST_BODY, references);
        collect(BRACKETED_KEY.matcher(title), FoundIn.PULL_REQUEST_TITLE, references);
        return List.copyOf(references);
    }

    private static void collect(Matcher matcher, FoundIn foundIn, Set<WorkPackageReference> references) {
        while (matcher.find()) {
            references.add(new WorkPackageReference(matcher.group(1), foundIn));
        }
    }
}
