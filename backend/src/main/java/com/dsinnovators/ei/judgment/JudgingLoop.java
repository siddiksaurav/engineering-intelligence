package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.job.JobCounts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loop of a model job over its subjects, in order: a subject that fails on its own is named and
 * the loop goes on; when every provider has reached its limit the loop stops, counts what is left
 * and succeeds, so the next run goes on with the rest (decision 152). A subject whose request waits
 * in the model exchange is counted in {@code left_for_exchange} and the loop goes on (decision 271).
 *
 * @param <T> what the job judges one at a time
 */
final class JudgingLoop<T> {

    private static final Logger LOG = LoggerFactory.getLogger(JudgingLoop.class);
    private static final String LEFT_FOR_EXCHANGE = "left_for_exchange";

    private final String leftForLaterCount;
    private final String subjectsName;
    private final ToLongFunction<T> sizeOf;
    private final Function<T, String> nameOf;

    /**
     * @param leftForLaterCount the key of the count of what a stop at the limit leaves
     * @param subjectsName what the subjects are, for the log line of a stop
     * @param sizeOf how much one subject adds to the count of what is left
     * @param nameOf how a failed subject is named in the failure of the run
     */
    JudgingLoop(String leftForLaterCount, String subjectsName, ToLongFunction<T> sizeOf, Function<T, String> nameOf) {
        this.leftForLaterCount = leftForLaterCount;
        this.subjectsName = subjectsName;
        this.sizeOf = sizeOf;
        this.nameOf = nameOf;
    }

    /**
     * Judges the subjects one after the other.
     *
     * @return the names of the subjects that failed, in order
     */
    List<String> run(List<T> subjects, Consumer<T> judge, JobCounts counts) {
        List<String> failed = new ArrayList<>();
        for (int index = 0; index < subjects.size(); index++) {
            T subject = subjects.get(index);
            try {
                judge.accept(subject);
            } catch (AnswerNotYetGivenException waiting) {
                counts.add(LEFT_FOR_EXCHANGE, sizeOf.applyAsLong(subject));
            } catch (ModelLimitReachedException limit) {
                LOG.warn("{}; the next run goes on with the {} not yet judged", limit.getMessage(), subjectsName);
                counts.add(leftForLaterCount, sizeLeftFrom(subjects, index));
                return failed;
            } catch (RuntimeException failure) {
                LOG.error("{} could not be judged", nameOf.apply(subject), failure);
                failed.add(nameOf.apply(subject));
            }
        }
        return failed;
    }

    private long sizeLeftFrom(List<T> subjects, int index) {
        return subjects.subList(index, subjects.size()).stream()
                .mapToLong(sizeOf)
                .sum();
    }
}
