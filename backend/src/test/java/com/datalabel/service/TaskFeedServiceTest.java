package com.datalabel.service;

import com.datalabel.domain.*;
import com.datalabel.repository.JobRepository;
import com.datalabel.repository.UnitRepository;
import com.datalabel.repository.WorkerTrustRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TaskFeedServiceTest {

    private UnitRepository units;
    private JobRepository jobs;
    private WorkerTrustRepository trusts;
    private TaskFeedService svc;

    private Job job(int cadence, int tasksPerPage) {
        Job j = new Job();
        j.setId(1L);
        j.setGoldCadence(cadence);
        j.setTasksPerPage(tasksPerPage);
        j.setEndOfWorkMessage(EndOfWorkMessage.COME_BACK_LATER);
        return j;
    }

    private Unit unit(long id) {
        Unit u = new Unit();
        u.setId(id); u.setJobId(1L);
        return u;
    }

    /** Stub: gold pool returns up to `limit` units with sequential ids starting at base. */
    private void mockGoldPool(long base, int available) {
        when(units.findRandomGoldNotJudgedBy(anyLong(), anyLong(), anyInt())).thenAnswer(inv -> {
            int limit = inv.getArgument(2);
            List<Unit> out = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, available); i++) out.add(unit(base + i));
            return out;
        });
    }

    private void mockNonGoldPool(long base, int available) {
        when(units.findRandomUnlabeledNotJudgedBy(anyLong(), anyLong(), anyInt())).thenAnswer(inv -> {
            int limit = inv.getArgument(2);
            List<Unit> out = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, available); i++) out.add(unit(base + i));
            return out;
        });
    }

    @BeforeEach void setup() {
        units = mock(UnitRepository.class);
        jobs = mock(JobRepository.class);
        trusts = mock(WorkerTrustRepository.class);
        svc = new TaskFeedService(units, jobs, trusts);
        when(jobs.findById(1L)).thenReturn(Optional.of(job(3, 4)));
        when(trusts.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    @Test void quarantinedWorkerGetsNothing() {
        when(trusts.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.of(
                WorkerTrust.builder().status(TrustStatus.QUARANTINED).build()));

        assertThat(svc.nextPageFor(1L, 10L, 4)).isEmpty();
        verify(units, never()).findRandomGoldNotJudgedBy(anyLong(), anyLong(), anyInt());
    }

    @Test void pageOf4WithCadence3HasOneGoldThreeNonGold() {
        mockGoldPool(1000, 50); mockNonGoldPool(2000, 50);

        List<Unit> page = svc.nextPageFor(1L, 10L, 4);

        assertThat(page).hasSize(4);
        long goldCount = page.stream().filter(u -> u.getId() >= 1000 && u.getId() < 2000).count();
        assertThat(goldCount).isEqualTo(1);
    }

    @Test void pageOf8WithCadence3HasTwoGoldSixNonGold() {
        mockGoldPool(1000, 50); mockNonGoldPool(2000, 50);

        List<Unit> page = svc.nextPageFor(1L, 10L, 8);

        assertThat(page).hasSize(8);
        long goldCount = page.stream().filter(u -> u.getId() >= 1000 && u.getId() < 2000).count();
        assertThat(goldCount).isEqualTo(2);
    }

    @Test void noGoldRemainingMeansEmptyPageEvenIfNonGoldExists() {
        mockGoldPool(1000, 0); mockNonGoldPool(2000, 50);

        assertThat(svc.nextPageFor(1L, 10L, 4)).isEmpty();
        verify(units, never()).findRandomUnlabeledNotJudgedBy(anyLong(), anyLong(), anyInt());
    }

    @Test void topsUpWithGoldWhenNonGoldExhausted() {
        // Worker burned through non-gold but gold remains — page stays full.
        mockGoldPool(1000, 50); mockNonGoldPool(2000, 0);

        List<Unit> page = svc.nextPageFor(1L, 10L, 4);

        assertThat(page).hasSize(4);
        long goldCount = page.stream().filter(u -> u.getId() >= 1000 && u.getId() < 2000).count();
        assertThat(goldCount).isEqualTo(4);
    }

    @Test void topsUpWithGoldWhenNonGoldShort() {
        mockGoldPool(1000, 50); mockNonGoldPool(2000, 1);

        List<Unit> page = svc.nextPageFor(1L, 10L, 4);

        assertThat(page).hasSize(4);
        long nonGoldCount = page.stream().filter(u -> u.getId() >= 2000).count();
        assertThat(nonGoldCount).isEqualTo(1);
    }

    @Test void smallerPageWhenBothPoolsRunOut() {
        mockGoldPool(1000, 2); mockNonGoldPool(2000, 0);

        List<Unit> page = svc.nextPageFor(1L, 10L, 4);

        assertThat(page).hasSize(2);
    }

    @Test void singleTaskPageWithLargeCadenceStillIncludesOneGold() {
        when(jobs.findById(1L)).thenReturn(Optional.of(job(10, 1)));
        mockGoldPool(1000, 50); mockNonGoldPool(2000, 50);

        List<Unit> page = svc.nextPageFor(1L, 10L, 1);

        assertThat(page).hasSize(1);
        assertThat(page.get(0).getId()).isGreaterThanOrEqualTo(1000L);
    }

    @Test void pageIsShuffledSoGoldPositionIsntPredictable() {
        int firstSlotIsGold = 0;
        int trials = 200;
        for (int i = 0; i < trials; i++) {
            mockGoldPool(-1L - i * 10L, 50);
            mockNonGoldPool(1L + i * 10L, 50);
            List<Unit> page = svc.nextPageFor(1L, 10L, 4);
            if (page.get(0).getId() < 0) firstSlotIsGold++;
        }
        assertThat(firstSlotIsGold).isLessThan(180).isGreaterThan(5);
    }
}
