package com.dsinnovators.ei.source.github;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GitHubProperties.class)
class GitHubConfiguration {

    private static final String API_VERSION = "2022-11-28";

    /**
     * Sets what every call to GitHub shares: the root, the media type, the API version, a name for
     * the client and the timeouts. The token is added per call by {@link GitHubApi}.
     */
    static RestClient.Builder configure(RestClient.Builder builder, GitHubProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        return builder.requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, "engineering-intelligence");
    }

    @Bean
    GitHubApi gitHubApi(GitHubProperties properties, Clock clock) {
        RestClient client = configure(RestClient.builder(), properties).build();
        return new GitHubApi(client, properties, clock, Thread::sleep);
    }
}
