package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The one rule for GitHub bot accounts (decision 84): an account GitHub types as {@code Bot}, or
 * whose login is in the setting {@code github.bot_logins}, is a bot. Bots never become persons and
 * never produce evidence.
 */
@Component
public class BotAccounts {

    private static final String BOT_TYPENAME = "Bot";
    private static final String BOT_LOGINS_SETTING = "github.bot_logins";

    private final SettingService settings;

    BotAccounts(SettingService settings) {
        this.settings = settings;
    }

    /** Whether the account is a bot, with the bot list in force in {@code quarter}. */
    public boolean isBot(String login, String typename, Quarter quarter) {
        return BOT_TYPENAME.equals(typename) || loginsOfBots(quarter).contains(login);
    }

    /** The logins on the bot list in force in {@code quarter}; accounts GitHub types {@code Bot} are not on it. */
    public List<String> loginsOfBots(Quarter quarter) {
        return settings.getTextList(BOT_LOGINS_SETTING, quarter);
    }
}
