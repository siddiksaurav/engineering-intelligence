package com.dsinnovators.ei.job;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobsProperties.class)
class JobConfiguration {

    // Each run gets its own virtual thread. Not a bean, so Spring Boot's own task executor is left
    // as it is. Stopped with the application; a run cut off this way is marked interrupted at the
    // next start.
    private final ExecutorService jobThreads = Executors.newVirtualThreadPerTaskExecutor();

    @Bean
    JobRegistry jobRegistry(ObjectProvider<Job> jobs) {
        return new JobRegistry(jobs.orderedStream().toList());
    }

    @Bean
    JobLauncher jobLauncher(JobRegistry registry, JobRunLog log, Clock clock, JobsProperties properties) {
        return new JobLauncher(registry, log, jobThreads, clock, properties);
    }

    @PreDestroy
    void stopRunningJobs() {
        jobThreads.shutdownNow();
    }
}
