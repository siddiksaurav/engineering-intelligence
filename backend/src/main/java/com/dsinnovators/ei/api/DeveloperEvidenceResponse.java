package com.dsinnovators.ei.api;

import java.util.List;

record DeveloperEvidenceResponse(PersonResponse person, List<EvidenceItemResponse> items) {}
