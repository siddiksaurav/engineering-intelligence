package com.dsinnovators.ei.source.github;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** A {@link GitHubApi} that talks to a mock server and only records its waits. */
public final class GitHubApiFixture {

    static final String TOKEN = "ghp_test-token-never-shown";
    static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");
    static final String GRAPHQL = "https://api.github.com/graphql";

    final List<Duration> waits = new ArrayList<>();
    final GitHubProperties properties;
    public final MockRestServiceServer server;
    final GitHubApi api;

    GitHubApiFixture() {
        this(TOKEN);
    }

    GitHubApiFixture(String token) {
        properties = new GitHubProperties(
                URI.create("https://api.github.com"), token, 50, 3, Duration.ofSeconds(2), Duration.ofSeconds(60));
        RestClient.Builder builder = GitHubConfiguration.configure(RestClient.builder(), properties);
        server = MockRestServiceServer.bindTo(builder).build();
        api = new GitHubApi(builder.build(), properties, Clock.fixed(NOW, ZoneOffset.UTC), waits::add);
    }
}
