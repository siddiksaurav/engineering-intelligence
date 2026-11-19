package com.dsinnovators.ei.source.openproject;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** An {@link OpenProjectApi} that talks to a mock server and only records its waits. */
final class OpenProjectApiFixture {

    static final String API_URL = "https://community.openproject.org/api/v3";
    static final Duration PAUSE = Duration.ofMillis(200);
    static final Duration RETRY_WAIT = Duration.ofSeconds(2);

    final List<Duration> waits = new ArrayList<>();
    final OpenProjectProperties properties;
    final MockRestServiceServer server;
    final OpenProjectApi api;

    OpenProjectApiFixture() {
        this(100);
    }

    OpenProjectApiFixture(int pageSize) {
        properties =
                new OpenProjectProperties(URI.create(API_URL), pageSize, PAUSE, 3, RETRY_WAIT, Duration.ofSeconds(60));
        RestClient.Builder builder = OpenProjectConfiguration.configure(RestClient.builder(), properties);
        server = MockRestServiceServer.bindTo(builder).build();
        api = new OpenProjectApi(builder.build(), properties, waits::add);
    }
}
