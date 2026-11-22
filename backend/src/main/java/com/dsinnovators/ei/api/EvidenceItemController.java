package com.dsinnovators.ei.api;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shows evidence item rows by id, for the pages that follow a score down to its items. */
@RestController
@RequestMapping("/api/evidence-items")
class EvidenceItemController {

    private final EvidenceItemSummaries summaries;
    private final int maxIds;

    EvidenceItemController(EvidenceItemSummaries summaries, @Value("${ei.api.max-evidence-item-ids}") int maxIds) {
        this.summaries = summaries;
        this.maxIds = maxIds;
    }

    /** @param ids comma-separated positive whole numbers */
    @GetMapping
    List<EvidenceItemSummaryResponse> summaries(@RequestParam String ids) {
        return summaries.ofIds(parse(ids));
    }

    @GetMapping("/{id}")
    EvidenceItemDetailResponse item(@PathVariable long id) {
        return summaries.detailOf(id).orElseThrow(() -> new EvidenceItemNotFoundException(id));
    }

    private List<Long> parse(String ids) {
        if (ids.isBlank()) {
            throw new InvalidEvidenceItemIdsException("Give at least one evidence item id in ids");
        }
        String[] parts = ids.split(",", -1);
        if (parts.length > maxIds) {
            throw new InvalidEvidenceItemIdsException(
                    "At most " + maxIds + " evidence item ids at a time, got " + parts.length);
        }
        return List.copyOf(new LinkedHashSet<>(Arrays.stream(parts)
                .map(EvidenceItemController::positiveWholeNumber)
                .toList()));
    }

    private static long positiveWholeNumber(String text) {
        try {
            long id = Long.parseLong(text.strip());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException notANumber) {
            // Falls through to the refusal below, which names the value.
        }
        throw new InvalidEvidenceItemIdsException("An evidence item id is a positive whole number, got '" + text + "'");
    }
}
