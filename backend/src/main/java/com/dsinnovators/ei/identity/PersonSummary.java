package com.dsinnovators.ei.identity;

/** A person as the readers of other packages see it: the id and the name to show. */
public record PersonSummary(long personId, String displayName) {

    static PersonSummary of(Person person) {
        return new PersonSummary(person.getId(), person.getDisplayName());
    }
}
