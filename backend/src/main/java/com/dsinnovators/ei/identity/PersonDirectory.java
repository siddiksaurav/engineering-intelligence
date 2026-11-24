package com.dsinnovators.ei.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Finds persons, and the person behind a GitHub account or an OpenProject user, for the features that follow. */
@Component
public class PersonDirectory {

    private final PersonIdentityRepository identities;
    private final PersonRepository people;

    PersonDirectory(PersonIdentityRepository identities, PersonRepository people) {
        this.identities = identities;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public Optional<PersonSummary> find(long personId) {
        return people.findById(personId).map(PersonSummary::of);
    }

    /** The persons with these ids, keyed by id. An unknown id is not in the map. */
    @Transactional(readOnly = true)
    public Map<Long, PersonSummary> find(Collection<Long> personIds) {
        return people.findAllById(personIds).stream()
                .map(PersonSummary::of)
                .collect(Collectors.toMap(PersonSummary::personId, Function.identity()));
    }

    /** The person id of the GitHub account with this database id, if it has become a person. */
    @Transactional(readOnly = true)
    public Optional<Long> personOfGitHubAccount(long databaseId) {
        return personOf(IdentitySource.GITHUB, databaseId);
    }

    /** The person id behind an OpenProject user, through a proposed or confirmed match (decision 95). */
    @Transactional(readOnly = true)
    public Optional<Long> personOfOpenProjectUser(long userId) {
        return personOf(IdentitySource.OPENPROJECT, userId);
    }

    private Optional<Long> personOf(String dataSourceKey, long externalId) {
        return identities
                .find(dataSourceKey, Long.toString(externalId))
                .map(identity -> identity.getPerson().getId());
    }
}
