package com.dsinnovators.ei.job;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JobConfiguration {

    @Bean
    JobRegistry jobRegistry(ObjectProvider<Job> jobs) {
        return new JobRegistry(jobs.orderedStream().toList());
    }
}
