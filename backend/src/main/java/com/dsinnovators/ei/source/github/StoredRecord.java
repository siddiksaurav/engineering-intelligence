package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.StoreResult;

/** What storing one GitHub record did. */
record StoredRecord(GitHubRecordKind kind, StoreResult.Outcome outcome) {}
