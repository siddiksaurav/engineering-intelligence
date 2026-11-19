package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FailedItemsTest {

    @Test
    void namesEveryItemUpToTheFirstTwenty() {
        assertEquals("#1, #2", FailedItems.named(List.of("#1", "#2")));
    }

    @Test
    void namesOnlyTheFirstTwentyOfMore() {
        List<String> names =
                IntStream.rangeClosed(1, 25).mapToObj(number -> "#" + number).toList();

        String expected = String.join(", ", names.subList(0, 20));

        assertEquals(expected, FailedItems.named(names));
    }
}
