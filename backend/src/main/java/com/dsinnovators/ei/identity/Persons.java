package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.source.DataSource;
import com.dsinnovators.ei.source.DataSourceRepository;
import com.dsinnovators.ei.source.github.GitHubAccount;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps one person per GitHub account. The identity is keyed by the account's database id, which
 * stays when the login is renamed (decision 82); the display name is the login (decision 96).
 */
@Component
public class Persons {

    /** What {@link #ensure} did. */
    public enum Outcome {
        CREATED,
        LOGIN_UPDATED,
        UNCHANGED
    }

    /** The person of the account and what was done to find or make it. */
    public record Result(long personId, Outcome outcome) {}

    private final PersonRepository people;
    private final PersonIdentityRepository identities;
    private final DataSourceRepository dataSources;

    Persons(PersonRepository people, PersonIdentityRepository identities, DataSourceRepository dataSources) {
        this.people = people;
        this.identities = identities;
        this.dataSources = dataSources;
    }

    /**
     * Creates the person and the GitHub identity of a new account, follows a renamed login, and
     * changes nothing for a known account whose login is the same. Callers skip bots first.
     */
    @Transactional
    public Result ensure(GitHubAccount account) {
        String externalId = Long.toString(account.databaseId());
        Optional<PersonIdentity> known = identities.find(IdentitySource.GITHUB, externalId);
        if (known.isEmpty()) {
            return create(account, externalId);
        }
        PersonIdentity identity = known.get();
        Person person = identity.getPerson();
        if (account.login().equals(identity.getUsername())) {
            return new Result(person.getId(), Outcome.UNCHANGED);
        }
        identity.rename(account.login());
        person.rename(account.login());
        return new Result(person.getId(), Outcome.LOGIN_UPDATED);
    }

    private Result create(GitHubAccount account, String externalId) {
        DataSource github = dataSources
                .findByKey(IdentitySource.GITHUB)
                .orElseThrow(() -> new IllegalStateException(
                        "The data source " + IdentitySource.GITHUB + " is not in the database"));
        Person person = people.save(new Person(account.login()));
        identities.save(new PersonIdentity(person, github, externalId, account.login()));
        return new Result(person.getId(), Outcome.CREATED);
    }
}
