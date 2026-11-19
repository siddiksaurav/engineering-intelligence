package com.dsinnovators.ei.job;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Starts a job and shows a run. Reached only from the same machine ({@link LocalCallerOnlyFilter}). */
@RestController
@RequestMapping(LocalCallerOnlyFilter.JOBS_PATH)
class JobController {

    static final String STARTED_BY_API = "api";

    private final JobLauncher launcher;
    private final JobRunLog log;

    JobController(JobLauncher launcher, JobRunLog log) {
        this.launcher = launcher;
        this.log = log;
    }

    /** Body: a JSON object of text arguments; none when the body is left out. */
    @PostMapping("/{jobName}/runs")
    ResponseEntity<JobRunResponse> start(@PathVariable String jobName, @RequestBody(required = false) JsonNode body) {
        JobRunView started = launcher.start(jobName, arguments(body), STARTED_BY_API);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(LocalCallerOnlyFilter.JOBS_PATH + "/runs/{id}")
                .buildAndExpand(started.id())
                .toUri();
        return ResponseEntity.accepted().location(location).body(JobRunResponse.of(started));
    }

    @GetMapping("/runs/{runId}")
    JobRunResponse run(@PathVariable long runId) {
        return log.find(runId).map(JobRunResponse::of).orElseThrow(() -> new JobRunNotFoundException(runId));
    }

    private static Map<String, String> arguments(JsonNode body) {
        Map<String, String> arguments = new TreeMap<>();
        if (body == null) {
            return arguments;
        }
        if (!body.isObject()) {
            throw new IllegalArgumentException("The arguments must be a JSON object of texts");
        }
        body.properties().forEach(argument -> {
            if (!argument.getValue().isString()) {
                throw new IllegalArgumentException(
                        "The argument '" + argument.getKey() + "' must be text, was " + argument.getValue());
            }
            arguments.put(argument.getKey(), argument.getValue().stringValue());
        });
        return arguments;
    }
}
