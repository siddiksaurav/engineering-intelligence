package com.dsinnovators.ei.source.openproject;

import tools.jackson.databind.JsonNode;

/** The work package type that the import treats specially. */
final class WorkPackageType {

    private static final String FEATURE = "Feature";
    private static final String BUG = "Bug";

    private WorkPackageType() {}

    /** The type name of the work package, as OpenProject returned it. */
    static String nameOf(JsonNode workPackage) {
        return workPackage.path("_links").path("type").path("title").asString();
    }

    /** Whether the work package, as OpenProject returned it, has the type Feature. */
    static boolean isFeature(JsonNode workPackage) {
        return FEATURE.equals(nameOf(workPackage));
    }

    /** Whether the work package, as OpenProject returned it, has the type Bug. */
    static boolean isBug(JsonNode workPackage) {
        return BUG.equals(nameOf(workPackage));
    }
}
