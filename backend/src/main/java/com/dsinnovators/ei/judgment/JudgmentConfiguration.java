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

    private static final String EXCHANGE_PROVIDER = "claude-code";

    /**
     * The model, as the chain of the configured providers, empty when no provider has a key; in
     * exchange mode the model exchange alone (decision 271).
     */
    @Bean
    ProviderChain providerChain(JudgmentProperties properties, ModelExchange exchange, Clock clock) {
        WaitMeter waitMeter = new WaitMeter(Thread::sleep);
        if (properties.mode() == JudgmentMode.EXCHANGE) {
            return new ProviderChain(
                    List.of(new ProviderChain.Provider(EXCHANGE_PROVIDER, new ExchangeModel(exchange))), waitMeter);
        }
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
