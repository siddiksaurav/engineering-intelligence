package com.dsinnovators.ei.source.openproject;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenProjectProperties.class)
class OpenProjectConfiguration {

    // Names the application and nothing else: no person, no address.
    private static final String USER_AGENT = "engineering-intelligence-poc";

    /** Sets what every call to OpenProject shares: the media type, a generic name for the client and the timeouts. */
    static RestClient.Builder configure(RestClient.Builder builder, OpenProjectProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        return builder.requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT);
    }

    @Bean
    OpenProjectApi openProjectApi(OpenProjectProperties properties) {
        RestClient client = configure(RestClient.builder(), properties).build();
        return new OpenProjectApi(client, properties, Thread::sleep);
    }
}
