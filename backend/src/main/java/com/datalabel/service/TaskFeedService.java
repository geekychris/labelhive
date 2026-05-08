package com.datalabel.service;

import com.datalabel.domain.Job;
import com.datalabel.domain.TrustStatus;
import com.datalabel.domain.Unit;
import com.datalabel.domain.WorkerTrust;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Worker feed. Two important rules enforced here:
 *
 * <ol>
 *   <li><b>Gold mixing.</b> A page of N tasks contains a controlled mix of gold +
 *       non-gold per the job's {@code goldCadence} (1 gold per N non-gold).
 *       The result is shuffled so the worker can't tell which is gold.</li>
 *   <li><b>Gold-gating.</b> If the worker has no gold left to judge, the feed
 *       returns <em>nothing</em> — even if non-gold remains. Without gold to
 *       ground against, we can't keep their trust score current.</li>
 * </ol>
 *
 * <p><b>Pages stay full when possible.</b> When one pool runs short — e.g. all
 * non-gold has been judged but gold remains — we top up from the other pool
 * to keep the page size consistent. The cadence target is best-effort, not a
 * hard contract: a worker who's burned through all non-gold will see
 * gold-heavy pages until they're done.</p>
 */
@Service
public class TaskFeedService {

    private final UnitRepository units;
    private final JobRepository jobs;
    private final WorkerTrustRepository trusts;

    public TaskFeedService(UnitRepository units, JobRepository jobs, WorkerTrustRepository trusts) {
        this.units = units; this.jobs = jobs; this.trusts = trusts;
    }

    /** Convenience for the legacy single-task feed. Returns at most one unit. */
    public Optional<Unit> nextFor(Long jobId, Long userId) {
        List<Unit> page = nextPageFor(jobId, userId, 1);
        return page.isEmpty() ? Optional.empty() : Optional.of(page.get(0));
    }

    public List<Unit> nextPageFor(Long jobId, Long userId, int requestedSize) {
        if (requestedSize < 1) requestedSize = 1;
        Job job = jobs.findById(jobId).orElseThrow();

        if (isQuarantined(userId, jobId)) return List.of();

        // Fetch up to `requestedSize` unique candidates from each pool in a single
        // query — avoids the duplicate hazard of N independent single-row picks.
        List<Unit> goldPool    = units.findRandomGoldNotJudgedBy(jobId, userId, requestedSize);
        if (goldPool.isEmpty()) return List.of();   // gold-gate
        List<Unit> nonGoldPool = units.findRandomUnlabeledNotJudgedBy(jobId, userId, requestedSize);

        int cadence = Math.max(1, job.getGoldCadence());
        // Cadence c → 1 gold per c non-gold → fraction 1/(c+1) → at least 1 per page.
        int goldTarget = Math.max(1, requestedSize / (cadence + 1));
        int nonGoldTarget = Math.max(0, requestedSize - goldTarget);

        List<Unit> page = new ArrayList<>(requestedSize);
        int gIdx = 0, ngIdx = 0;

        while (gIdx < goldTarget && gIdx < goldPool.size())
            page.add(goldPool.get(gIdx++));
        while (ngIdx < nonGoldTarget && ngIdx < nonGoldPool.size())
            page.add(nonGoldPool.get(ngIdx++));

        // Top up the page from whichever pool still has units. Quality bias:
        // try gold first since it keeps trust meaningful.
        while (page.size() < requestedSize && (gIdx < goldPool.size() || ngIdx < nonGoldPool.size())) {
            if (gIdx < goldPool.size())      page.add(goldPool.get(gIdx++));
            else                             page.add(nonGoldPool.get(ngIdx++));
        }

        Collections.shuffle(page);
        return page;
    }

    private boolean isQuarantined(Long userId, Long jobId) {
        Optional<WorkerTrust> trust = trusts.findByUserIdAndJobId(userId, jobId);
        return trust.isPresent() && trust.get().getStatus() == TrustStatus.QUARANTINED;
    }
}
