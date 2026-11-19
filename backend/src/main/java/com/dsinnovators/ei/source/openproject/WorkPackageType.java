package com.dsinnovators.ei.source.openproject;

import tools.jackson.databind.JsonNode;

/** The work package type that the import treats specially. */
final class WorkPackageType {

    private static final String FEATURE = "Feature";

    private WorkPackageType() {}

    /** Whether the work package, as OpenProject returned it, has the type Feature. */
    static boolean isFeature(JsonNode workPackage) {
        return FEATURE.equals(
                workPackage.path("_links").path("type").path("title").asString());
    }
}
