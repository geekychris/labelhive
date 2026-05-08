package com.datalabel.repository;

import com.datalabel.domain.Job;
import com.datalabel.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByStatus(JobStatus status);
    boolean existsByName(String name);
}
