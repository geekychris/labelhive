package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.WorkerTrustRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TrustService {

    private final WorkerTrustRepository trusts;
    private final JobRepository jobs;

    public TrustService(WorkerTrustRepository trusts, JobRepository jobs) {
        this.trusts = trusts; this.jobs = jobs;
    }

    @Transactional
    public WorkerTrust recordGoldOutcome(Long userId, Long jobId, boolean correct) {
        Job job = jobs.findById(jobId).orElseThrow();
        WorkerTrust t = trusts.findByUserIdAndJobId(userId, jobId)
                .orElseGet(() -> WorkerTrust.builder()
                        .userId(userId).jobId(jobId)
                        .goldAttempts(0).goldCorrect(0)
                        .accuracy(BigDecimal.ONE)
                        .status(TrustStatus.OK)
                        .build());
        t.setGoldAttempts(t.getGoldAttempts() + 1);
        if (correct) t.setGoldCorrect(t.getGoldCorrect() + 1);
        BigDecimal acc = BigDecimal.valueOf(t.getGoldCorrect())
                .divide(BigDecimal.valueOf(t.getGoldAttempts()), 4, RoundingMode.HALF_UP);
        t.setAccuracy(acc);

        // Quarantine after a small minimum sample so a single gold miss doesn't bench someone.
        if (t.getGoldAttempts() >= 5 && acc.compareTo(job.getTrustFloor()) < 0) {
            t.setStatus(TrustStatus.QUARANTINED);
        } else if (acc.compareTo(job.getTrustFloor()) >= 0) {
            t.setStatus(TrustStatus.OK);
        }
        return trusts.save(t);
    }
}
