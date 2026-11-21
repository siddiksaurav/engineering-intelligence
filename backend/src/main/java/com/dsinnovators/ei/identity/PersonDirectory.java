package com.dsinnovators.ei.identity;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Finds the person behind a GitHub account or an OpenProject user, for the features that follow. */
@Component
public class PersonDirectory {

    private final PersonIdentityRepository identities;

    PersonDirectory(PersonIdentityRepository identities) {
        this.identities = identities;
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
