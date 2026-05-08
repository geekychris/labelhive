package com.datalabel.repository;

import com.datalabel.domain.WorkerTrust;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkerTrustRepository extends JpaRepository<WorkerTrust, WorkerTrust.PK> {
    Optional<WorkerTrust> findByUserIdAndJobId(Long userId, Long jobId);
    List<WorkerTrust> findByJobId(Long jobId);
    List<WorkerTrust> findByUserId(Long userId);
}
