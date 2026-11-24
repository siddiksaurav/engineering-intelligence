package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.source.github.GitHubAccount;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PersonDirectoryTest extends DatabaseTest {

    @Autowired
    private Persons persons;

    @Autowired
    private PersonDirectory directory;

    @Test
    void aGitHubAccountIsFoundByItsDatabaseId() {
        long personId =
                persons.ensure(new GitHubAccount(80250, "akabiru", "User")).personId();

        assertEquals(Optional.of(personId), directory.personOfGitHubAccount(80250));
        assertEquals(Optional.empty(), directory.personOfGitHubAccount(1));
    }

    @Test
    void anOpenProjectUserIsFoundByItsIdWhenAnOpenProjectIdentityExists() {
        long personId =
                persons.ensure(new GitHubAccount(80250, "akabiru", "User")).personId();
        jdbc.update("""
                insert into person_identity (person_id, data_source_id, external_id, username)
                values (?, (select id from data_source where key = 'openproject'), '80251', 'Kabiru Mwenja')
                """, personId);

        assertEquals(Optional.of(personId), directory.personOfOpenProjectUser(80251));
        assertEquals(Optional.empty(), directory.personOfOpenProjectUser(80250));
    }

    @Test
    void aPersonIsFoundById() {
        long grace = persons.ensure(new GitHubAccount(2, "grace", "User")).personId();
        long ada = persons.ensure(new GitHubAccount(1, "ada", "User")).personId();

        assertEquals(Optional.of(new PersonSummary(ada, "ada")), directory.find(ada));
        assertEquals(Optional.empty(), directory.find(ada + grace));
    }
}
