package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.StoreResult;

/** What storing one OpenProject record did. */
record StoredRecord(OpenProjectRecordKind kind, StoreResult.Outcome outcome) {}
