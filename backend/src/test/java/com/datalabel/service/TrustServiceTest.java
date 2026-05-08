package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.WorkerTrustRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrustServiceTest {

    private WorkerTrustRepository trusts;
    private JobRepository jobs;
    private TrustService svc;

    private Job job(BigDecimal floor) {
        Job j = new Job();
        j.setId(1L);
        j.setTrustFloor(floor);
        return j;
    }

    @BeforeEach void setup() {
        trusts = mock(WorkerTrustRepository.class);
        jobs = mock(JobRepository.class);
        svc = new TrustService(trusts, jobs);
        when(trusts.save(any(WorkerTrust.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void firstAttemptCreatesTrustRecord() {
        when(jobs.findById(1L)).thenReturn(Optional.of(job(new BigDecimal("0.7"))));
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.empty());

        WorkerTrust t = svc.recordGoldOutcome(10L, 1L, true);

        assertThat(t.getGoldAttempts()).isEqualTo(1);
        assertThat(t.getGoldCorrect()).isEqualTo(1);
        assertThat(t.getAccuracy()).isEqualByComparingTo("1.0000");
        assertThat(t.getStatus()).isEqualTo(TrustStatus.OK);
    }

    @Test void quarantinesAfterFiveBadAttempts() {
        when(jobs.findById(1L)).thenReturn(Optional.of(job(new BigDecimal("0.7"))));
        WorkerTrust existing = WorkerTrust.builder()
                .userId(10L).jobId(1L)
                .goldAttempts(4).goldCorrect(1)
                .accuracy(new BigDecimal("0.2500"))
                .status(TrustStatus.OK).build();
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.of(existing));

        WorkerTrust t = svc.recordGoldOutcome(10L, 1L, false);

        assertThat(t.getGoldAttempts()).isEqualTo(5);
        assertThat(t.getGoldCorrect()).isEqualTo(1);
        assertThat(t.getStatus()).isEqualTo(TrustStatus.QUARANTINED);
    }

    @Test void doesNotQuarantineUnderMinimumSample() {
        when(jobs.findById(1L)).thenReturn(Optional.of(job(new BigDecimal("0.7"))));
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.empty());

        // Single bad outcome — sample size 1, should NOT quarantine.
        WorkerTrust t = svc.recordGoldOutcome(10L, 1L, false);

        assertThat(t.getGoldAttempts()).isEqualTo(1);
        assertThat(t.getStatus()).isEqualTo(TrustStatus.OK);
    }

    @Test void recoversFromQuarantineWhenAccuracyClimbsBack() {
        when(jobs.findById(1L)).thenReturn(Optional.of(job(new BigDecimal("0.7"))));
        WorkerTrust existing = WorkerTrust.builder()
                .userId(10L).jobId(1L)
                .goldAttempts(10).goldCorrect(7) // 70% — exactly at floor
                .accuracy(new BigDecimal("0.7000"))
                .status(TrustStatus.QUARANTINED).build();
        when(trusts.findByUserIdAndJobId(10L, 1L)).thenReturn(Optional.of(existing));

        WorkerTrust t = svc.recordGoldOutcome(10L, 1L, true);

        assertThat(t.getGoldAttempts()).isEqualTo(11);
        assertThat(t.getStatus()).isEqualTo(TrustStatus.OK);
    }
}
