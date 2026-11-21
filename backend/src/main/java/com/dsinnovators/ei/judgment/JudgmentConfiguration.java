package com.dsinnovators.ei.judgment;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JudgmentProperties.class)
class JudgmentConfiguration {

    /** The model, as the chain of the configured providers; empty when no provider has a key. */
    @Bean
    ProviderChain providerChain(JudgmentProperties properties, Clock clock) {
        WaitMeter waitMeter = new WaitMeter(Thread::sleep);
        List<ProviderChain.Provider> providers = properties.providers().stream()
                .map(provider ->
                        new ProviderChain.Provider(provider.name(), clientOf(provider, properties, waitMeter, clock)))
                .toList();
        return new ProviderChain(providers, waitMeter);
    }

    private static OpenAiCompatibleClient clientOf(
            JudgmentProvider provider, JudgmentProperties properties, WaitMeter waitMeter, Clock clock) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        RestClient restClient =
                RestClient.builder().requestFactory(requestFactory).build();
        return new OpenAiCompatibleClient(restClient, provider, properties.callPolicy(), waitMeter, clock);
    }
}
