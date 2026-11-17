package com.dsinnovators.ei;

import com.dsinnovators.ei.job.JobRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the web server, or, when started with {@code --job=<name>}, runs that one job without
 * opening the web port and exits with 0 on success and 1 on failure.
 */
@SpringBootApplication
public class EiApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(EiApplication.class);
        if (!isJobMode(args)) {
            application.run(args);
            return;
        }
        application.setWebApplicationType(WebApplicationType.NONE);
        int exitCode;
        try (ConfigurableApplicationContext context = application.run(args)) {
            exitCode = context.getBean(JobRunner.class).run(args);
        }
        System.exit(exitCode);
    }

    static boolean isJobMode(String[] args) {
        for (String arg : args) {
            if (arg.equals("--job") || arg.startsWith("--job=")) {
                return true;
            }
        }
        return false;
    }
}
