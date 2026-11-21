package com.dsinnovators.ei.source.openproject;

import java.util.Optional;

/**
 * What a judgment reads of a stored work package: its words, not its numbers.
 *
 * @param type the tracker type name, such as {@code Feature} or {@code Bug}
 * @param priority the priority name; empty when the work package has none
 * @param parentTitle the title of the parent work package; empty when it has none
 * @param description the Markdown text of the description; empty text when there is none
 * @param url the work package on OpenProject
 */
public record WorkPackageText(
        long number,
        String title,
        String type,
        Optional<String> priority,
        Optional<String> parentTitle,
        String description,
        String url) {}
