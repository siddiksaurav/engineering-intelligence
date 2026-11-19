package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.DataSource;
import com.dsinnovators.ei.source.DataSourceRepository;
import org.springframework.stereotype.Component;

/** The {@code openproject} data source, as the recorders need it. */
@Component
class OpenProjectSource {

    static final String KEY = "openproject";

    private final DataSourceRepository dataSources;

    OpenProjectSource(DataSourceRepository dataSources) {
        this.dataSources = dataSources;
    }

    DataSource dataSource() {
        return dataSources
                .findByKey(KEY)
                .orElseThrow(() -> new IllegalStateException("The data source " + KEY + " is not in the database"));
    }
}
