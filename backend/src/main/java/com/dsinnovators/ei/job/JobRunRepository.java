package com.dsinnovators.ei.job;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JobRunRepository extends JpaRepository<JobRun, Long> {

    Optional<JobRun> findByJobNameAndStatus(String jobName, JobRunStatus status);

    @Modifying
    @Query(
            nativeQuery = true,
            value = "update job_run set status = 'interrupted', finished_at = :finishedAt,"
                    + " error_message = :errorMessage where status = 'running'")
    int markRunningAsInterrupted(@Param("finishedAt") Instant finishedAt, @Param("errorMessage") String errorMessage);
}
