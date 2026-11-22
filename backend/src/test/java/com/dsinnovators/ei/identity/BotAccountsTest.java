package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BotAccountsTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private BotAccounts botAccounts;

    @Test
    void anAccountOnTheBotListIsABotEvenWhenGitHubTypesItUser() {
        assertTrue(botAccounts.isBot("openprojectci", "User", QUARTER));
    }

    @Test
    void anAccountGitHubTypesBotIsABot() {
        assertTrue(botAccounts.isBot("dependabot", "Bot", QUARTER));
    }

    @Test
    void anAccountGitHubTypesBotIsABotEvenWhenItIsNotOnTheList() {
        assertTrue(botAccounts.isBot("some-new-agent", "Bot", QUARTER));
    }

    @Test
    void aHumanAccountIsNotABot() {
        assertFalse(botAccounts.isBot("akabiru", "User", QUARTER));
    }

    @Test
    void theBotListIsTheLoginsOfTheSetting() {
        assertTrue(botAccounts.loginsOfBots(QUARTER).contains("openprojectci"));
    }
}
