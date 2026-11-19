-- GitHub logins that are bots (F02 design 6.1, decision 42). F02 stores bot records like any other;
-- F04 and F05 read this list. Checked on 19 Nov 2026 against opf/openproject.
insert into setting (key, valid_from_quarter, value, description) values (
    'github.bot_logins',
    '2026-Q2',
    '["dependabot", "openprojectci", "copilot-pull-request-reviewer", "github-actions", "github-advanced-security"]',
    'GitHub logins that are bots. An account GitHub types as Bot is also a bot. Read by F04 and F05; F02 stores bot records like any other.'
);
