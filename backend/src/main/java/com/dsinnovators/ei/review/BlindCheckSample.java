package com.dsinnovators.ei.review;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.judgment.CurrentItemJudgments;
import com.dsinnovators.ei.judgment.ItemJudgment;
import com.dsinnovators.ei.judgment.JudgmentKind;
import com.dsinnovators.ei.setting.SettingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The balanced sample of Feature work items that the user labels blind (F08 design, flow 1). It is
 * picked once: a second call answers with the stored rows. The order of the picks, and so the order of
 * the rows of the exported file, follows a hash of the item id, which does not depend on the AI's answer.
 */
@Service
public class BlindCheckSample {

    static final String ITEMS_PER_COMPLEXITY_SETTING = "blind_check.items_per_complexity";

    /** Very high is not picked: the PoC has no such item, and the check is of the three tiers (decision 345). */
    private static final List<String> PICKED_COMPLEXITIES = List.of("low", "medium", "high");

    private final BlindCheckItemRepository sampleItems;
    private final CurrentEvidenceItems evidenceItems;
    private final CurrentItemJudgments judgments;
    private final SettingService settings;
    private final Clock clock;

    BlindCheckSample(
            BlindCheckItemRepository sampleItems,
            CurrentEvidenceItems evidenceItems,
            CurrentItemJudgments judgments,
            SettingService settings,
            Clock clock) {
        this.sampleItems = sampleItems;
        this.evidenceItems = evidenceItems;
        this.judgments = judgments;
        this.settings = settings;
        this.clock = clock;
    }

    /** The stored sample in the order of its positions; empty before the first {@link #pick()}. */
    @Transactional(readOnly = true)
    public List<BlindCheckItem> stored() {
        return sampleItems.findAllByOrderByPositionAsc();
    }

    /**
     * Picks and stores the sample when none is stored, and answers with the stored sample.
     *
     * @throws InsufficientItemsException when a complexity has fewer items than the setting asks for;
     *     nothing is stored then
     */
    @Transactional
    public List<BlindCheckItem> pick() {
        if (sampleItems.existsBy()) {
            return stored();
        }
        // PostgreSQL keeps microseconds; the first answer must equal the stored one.
        Instant pickedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Candidate> picked = pickedCandidates();
        List<BlindCheckItem> sample = new ArrayList<>();
        for (int index = 0; index < picked.size(); index++) {
            Candidate candidate = picked.get(index);
            sample.add(sampleItems.save(
                    new BlindCheckItem(candidate.itemId(), candidate.judgmentId(), index + 1, pickedAt)));
        }
        return sample;
    }

    private List<Candidate> pickedCandidates() {
        int itemsPerComplexity = settings.getInt(ITEMS_PER_COMPLEXITY_SETTING, Quarter.of(LocalDate.now(clock)));
        Map<String, List<Candidate>> byComplexity =
                candidates().stream().collect(Collectors.groupingBy(Candidate::complexity));
        List<Candidate> picked = new ArrayList<>();
        for (String complexity : PICKED_COMPLEXITIES) {
            List<Candidate> available = byComplexity.getOrDefault(complexity, List.of());
            if (available.size() < itemsPerComplexity) {
                throw new InsufficientItemsException(complexity, available.size(), itemsPerComplexity);
            }
            picked.addAll(available.subList(0, itemsPerComplexity));
        }
        picked.sort(Comparator.comparing(Candidate::hashOfItemId));
        return picked;
    }

    /** Every current Feature work item with a current complexity judgment, in the order of the hash of its id. */
    private List<Candidate> candidates() {
        List<EvidenceItem> items = evidenceItems.ofType(EvidenceType.FEATURE_WORK);
        Map<Long, ItemJudgment> current = judgments.ofItems(
                JudgmentKind.FEATURE_WORK_COMPLEXITY,
                items.stream().map(EvidenceItem::getId).toList());
        return items.stream()
                .filter(item -> current.containsKey(item.getId()))
                .map(item -> Candidate.of(item.getId(), current.get(item.getId())))
                .sorted(Comparator.comparing(Candidate::hashOfItemId))
                .toList();
    }

    private record Candidate(long itemId, long judgmentId, String complexity, String hashOfItemId) {

        static Candidate of(long itemId, ItemJudgment judgment) {
            return new Candidate(
                    itemId, judgment.id(), judgment.value().path("complexity").asString(), sha256(itemId));
        }

        private static String sha256(long itemId) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(Long.toString(itemId).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
            }
        }
    }
}
