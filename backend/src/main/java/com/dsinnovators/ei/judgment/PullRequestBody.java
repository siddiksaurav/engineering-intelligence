package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.source.RawRecord;
import tools.jackson.databind.JsonNode;

/** Reads the description of a stored pull request, which GitHub leaves out of the record when it is empty. */
final class PullRequestBody {

    private PullRequestBody() {}

    static String of(RawRecord pullRequest) {
        JsonNode body = pullRequest.getPayload().path("body");
        return body.isString() ? body.asString() : "";
    }
}
