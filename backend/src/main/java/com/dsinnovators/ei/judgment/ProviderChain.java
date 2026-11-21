package com.dsinnovators.ei.judgment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The model, reached through an ordered list of free providers, each within its own limits
 * (decision 146). A request goes to the first provider that has not reached its limit in this run.
 * A provider that reaches its limit is not asked again until {@link #startRun()}; when none is left
 * the chain throws {@link ModelLimitReachedException}. Any other failure is not hidden by moving to
 * the next provider: it reaches the caller.
 */
final class ProviderChain implements JudgmentModel {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderChain.class);

    /** A provider's name and the model client that reaches it. */
    record Provider(String name, JudgmentModel model) {}

    private final List<Provider> providers;
    private final WaitMeter waitMeter;
    private final Set<String> atTheirLimit = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> answeredCalls = new ConcurrentHashMap<>();

    /** @param waitMeter the meter of the sleeper the providers' clients wait with */
    ProviderChain(List<Provider> providers, WaitMeter waitMeter) {
        this.providers = List.copyOf(providers);
        this.waitMeter = waitMeter;
    }

    @Override
    public ModelAnswer judge(ModelRequest request) {
        for (Provider provider : providers) {
            if (atTheirLimit.contains(provider.name())) {
                continue;
            }
            try {
                ModelAnswer answer = provider.model().judge(request);
                answeredCalls.merge(provider.name(), 1L, Long::sum);
                return answer;
            } catch (ProviderLimitReachedException limit) {
                LOG.warn("{}; moving to the next provider", limit.getMessage());
                atTheirLimit.add(provider.name());
            }
        }
        throw new ModelLimitReachedException(
                providers.isEmpty()
                        ? "No model provider is configured. Set LLM_PROVIDER_1_API_KEY"
                        : "Every model provider has reached its limit: " + providerNames());
    }

    /** Forgets the limits, the counts and the waits of the last run. */
    void startRun() {
        atTheirLimit.clear();
        answeredCalls.clear();
        waitMeter.reset();
    }

    /** The time the providers' clients waited since {@link #startRun()}, for a rate limit or before a retry. */
    Duration waited() {
        return waitMeter.waited();
    }

    /** The providers in the order they are tried. The job that needs the model fails at its start when this is empty. */
    List<String> providerNames() {
        return providers.stream().map(Provider::name).toList();
    }

    /** The calls each provider answered since {@link #startRun()}; a provider with none shows zero. */
    Map<String, Long> callsPerProvider() {
        Map<String, Long> calls = new LinkedHashMap<>();
        providers.forEach(provider -> calls.put(provider.name(), answeredCalls.getOrDefault(provider.name(), 0L)));
        return calls;
    }
}
