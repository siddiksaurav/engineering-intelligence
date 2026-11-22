package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Finds the working tier of a dimension from a person's scored items (spec 9.3). */
public final class WorkingTierCalculator {

    private final ScoringSettings settings;

    public WorkingTierCalculator(ScoringSettings settings) {
        this.settings = settings;
    }

    /** Works out the tier for {@code items}, the person's items of the quarter; types the dimension does not map are ignored. */
    public WorkingTier calculate(DimensionMapping mapping, Level level, List<ScoredItem> items) {
        List<EvidenceTypeTrail> trails = mapping.evidenceTypes().stream()
                .filter(MappedEvidenceType::tiered)
                .map(mapped -> trailOf(mapped, level, items))
                .toList();
        LevelThreeRule levelThreeRule = levelThreeRule(mapping, level, trails);
        Tier tier = Arrays.stream(Tier.values())
                .sorted(Comparator.reverseOrder())
                .filter(candidate -> hasPresence(trails, candidate))
                .filter(candidate -> candidate != Tier.T3 || levelThreeRule == null || levelThreeRule.met())
                .findFirst()
                .orElse(null);
        return new WorkingTier(tier, trails, levelThreeRule);
    }

    private EvidenceTypeTrail trailOf(MappedEvidenceType mapped, Level level, List<ScoredItem> items) {
        EvidenceType type = mapped.evidenceType();
        List<ScoredItem> ofType =
                items.stream().filter(item -> item.evidenceType() == type).toList();
        PresenceRule rule =
                settings.frequentEvidenceTypes().contains(type) ? PresenceRule.SHARE : PresenceRule.ONE_ITEM;
        if (!mapped.isReadAt(level)) {
            return new EvidenceTypeTrail(
                    type.databaseName(), rule, List.of(), List.of(), List.of(), trailItems(ofType), List.of());
        }
        List<ScoredItem> counted = select(ofType, ScoredItem::isJudged);
        return new EvidenceTypeTrail(
                type.databaseName(),
                rule,
                trailItems(counted),
                trailItems(select(ofType, item -> item.judgmentState() == JudgmentState.NOT_JUDGED_YET)),
                trailItems(select(ofType, item -> item.judgmentState() == JudgmentState.NOT_SUBSTANTIVE)),
                List.of(),
                presenceOf(rule, counted));
    }

    private List<TierPresence> presenceOf(PresenceRule rule, List<ScoredItem> counted) {
        long total = counted.stream().mapToLong(this::weightOf).sum();
        return Arrays.stream(Tier.values())
                .map(tier -> {
                    long atOrAbove = counted.stream()
                            .filter(item -> item.tier().isAtLeast(tier))
                            .mapToLong(this::weightOf)
                            .sum();
                    return new TierPresence(tier, atOrAbove, total, isPresent(rule, atOrAbove, total));
                })
                .toList();
    }

    private boolean isPresent(PresenceRule rule, long atOrAbove, long total) {
        return switch (rule) {
            case SHARE -> total > 0 && settings.presenceThreshold().isReachedBy(atOrAbove, total);
            case ONE_ITEM -> atOrAbove > 0;
        };
    }

    private LevelThreeRule levelThreeRule(DimensionMapping mapping, Level level, List<EvidenceTypeTrail> trails) {
        Integer needed = settings.t3ItemsNeededAtLevel3().get(mapping.key());
        if (level != Level.L3 || needed == null) {
            return null;
        }
        long t3Items = trails.stream()
                .flatMap(trail -> trail.counted().stream())
                .filter(item -> Tier.T3.name().equals(item.tier()))
                .count();
        return new LevelThreeRule(t3Items, needed, t3Items >= needed);
    }

    private static boolean hasPresence(List<EvidenceTypeTrail> trails, Tier tier) {
        return trails.stream()
                .flatMap(trail -> trail.presence().stream())
                .anyMatch(presence -> presence.tier() == tier && presence.presence());
    }

    /** The weight of an item in a presence sum: the tier weight for Feature work, else one per item. */
    long weightOf(ScoredItem item) {
        if (item.evidenceType() != EvidenceType.FEATURE_WORK) {
            return 1;
        }
        return Optional.ofNullable(settings.featureWorkWeights().get(item.tier()))
                .orElseThrow(() -> new IllegalStateException("No feature work weight in the settings for tier "
                        + item.tier() + " of item " + item.itemKey()));
    }

    private List<TrailItem> trailItems(List<ScoredItem> items) {
        return items.stream()
                .map(item -> TrailItem.of(item, item.isJudged() ? (int) weightOf(item) : null))
                .toList();
    }

    private static List<ScoredItem> select(List<ScoredItem> items, Predicate<ScoredItem> condition) {
        return items.stream().filter(condition).toList();
    }
}
