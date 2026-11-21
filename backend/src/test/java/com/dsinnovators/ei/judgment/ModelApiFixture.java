package com.dsinnovators.ei.judgment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** An {@link OpenAiCompatibleClient} that talks to a mock server and only records its waits. */
final class ModelApiFixture {

    static final String KEY = "gsk_test-key-never-shown";
    static final String CHAT_COMPLETIONS = "https://api.groq.com/openai/v1/chat/completions";
    static final CallPolicy POLICY = new CallPolicy(2, Duration.ofSeconds(2), Duration.ofSeconds(60), 300);

    final List<Duration> waits = new ArrayList<>();
    final MockRestServiceServer server;
    final OpenAiCompatibleClient client;

    ModelApiFixture() {
        this(new JudgmentProvider("groq", "https://api.groq.com/openai/v1", KEY, "openai/gpt-oss-120b", "low"));
    }

    ModelApiFixture(JudgmentProvider provider) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiCompatibleClient(builder.build(), provider, POLICY, waits::add);
    }

    /** A chat completion answer holding the given JSON text as the model's message. */
    static String completion(String model, String content, int promptTokens, int completionTokens) {
        return """
                {"id":"chatcmpl-1","model":"%s",
                 "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":%s}}],
                 "usage":{"prompt_tokens":%d,"completion_tokens":%d,"total_tokens":%d}}
                """.formatted(
                        model,
                        tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(content),
                        promptTokens,
                        completionTokens,
                        promptTokens + completionTokens);
    }
}
