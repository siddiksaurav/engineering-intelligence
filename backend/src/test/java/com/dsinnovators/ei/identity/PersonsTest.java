package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.identity.Persons.Outcome;
import com.dsinnovators.ei.source.github.GitHubAccount;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PersonsTest extends DatabaseTest {

    private static final long DATABASE_ID = 80250L;

    @Autowired
    private Persons persons;

    private static GitHubAccount account(String login) {
        return new GitHubAccount(DATABASE_ID, login, "User");
    }

    private List<Map<String, Object>> githubIdentities() {
        return jdbc.queryForList("""
                select p.display_name, i.username, i.external_id
                from person_identity i
                join person p on p.id = i.person_id
                join data_source d on d.id = i.data_source_id
                where d.key = 'github'
                """);
    }

    private int personCount() {
        return jdbc.queryForObject("select count(*) from person", Integer.class);
    }

    @Test
    void aRenamedLoginKeepsThePersonAndUpdatesTheUsernameAndDisplayName() {
        Persons.Result created = persons.ensure(account("akabiru"));

        Persons.Result renamed = persons.ensure(account("kabiru-mwenja"));

        assertEquals(Outcome.LOGIN_UPDATED, renamed.outcome());
        assertEquals(created.personId(), renamed.personId());
        assertEquals(1, personCount());
        assertEquals(
                List.of(Map.of("display_name", "kabiru-mwenja", "username", "kabiru-mwenja", "external_id", "80250")),
                githubIdentities());
    }

    @Test
    void aNewAccountCreatesOnePersonWithItsGitHubIdentity() {
        Persons.Result result = persons.ensure(account("akabiru"));

        assertEquals(Outcome.CREATED, result.outcome());
        assertEquals(1, personCount());
        assertEquals(
                List.of(Map.of("display_name", "akabiru", "username", "akabiru", "external_id", "80250")),
                githubIdentities());
    }

    @Test
    void aKnownUnchangedAccountCreatesAndChangesNothing() {
        Persons.Result first = persons.ensure(account("akabiru"));

        Persons.Result second = persons.ensure(account("akabiru"));

        assertEquals(Outcome.UNCHANGED, second.outcome());
        assertEquals(first.personId(), second.personId());
        assertEquals(1, personCount());
        assertEquals(1, githubIdentities().size());
    }
}
