package com.dsinnovators.ei.source.openproject;

import tools.jackson.databind.JsonNode;

/**
 * A work package as OpenProject gave it, with what hangs on it.
 *
 * @param number the work package number
 * @param workPackage the collection element, unchanged
 * @param activities the answer of the activities call, unchanged
 * @param pullRequestList the reduced list of linked pull requests (decision 69)
 */
record FetchedWorkPackage(long number, JsonNode workPackage, JsonNode activities, JsonNode pullRequestList) {}
