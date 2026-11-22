package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.common.Quarter;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class DeveloperAnalyticsTest {

    @Test
    void readsEveryQueryFromOneSnapshotOfTheData() throws NoSuchMethodException {
        Method method = DeveloperAnalytics.class.getMethod("of", long.class, Quarter.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
    }
}
