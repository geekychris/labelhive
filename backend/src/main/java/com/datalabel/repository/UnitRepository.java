package com.datalabel.repository;

import com.datalabel.domain.Unit;
import com.datalabel.domain.UnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    long countByJobId(Long jobId);

    long countByJobIdAndGoldAnswerIsNotNull(Long jobId);

    List<Unit> findByJobId(Long jobId);

    List<Unit> findByJobIdAndStatus(Long jobId, UnitStatus status);

    /**
     * Up to {@code limit} random open gold units the user has not yet judged.
     * Single query so the result has no duplicates — important for paged feeds.
     */
    @Query(value = """
            select u.* from unit u
            where u.job_id = :jobId
              and u.gold_answer is not null
              and u.status = 'OPEN'
              and not exists (
                  select 1 from judgment j
                   where j.unit_id = u.id and j.user_id = :userId
              )
            order by random()
            limit :limit
            """, nativeQuery = true)
    List<Unit> findRandomGoldNotJudgedBy(@Param("jobId") Long jobId,
                                         @Param("userId") Long userId,
                                         @Param("limit") int limit);

    /**
     * Up to {@code limit} random open non-gold units that still need more
     * judgments (per the unit's running target) and the user has not yet judged.
     */
    @Query(value = """
            select u.* from unit u
            where u.job_id = :jobId
              and u.gold_answer is null
              and u.status = 'OPEN'
              and u.judgment_count < u.judgments_needed
              and not exists (
                  select 1 from judgment jd
                   where jd.unit_id = u.id and jd.user_id = :userId
              )
            order by random()
            limit :limit
            """, nativeQuery = true)
    List<Unit> findRandomUnlabeledNotJudgedBy(@Param("jobId") Long jobId,
                                              @Param("userId") Long userId,
                                              @Param("limit") int limit);

    /** Exemplar units, ordered by id for stable display on the worker instructions page. */
    List<Unit> findByJobIdAndExemplarTrueOrderByIdAsc(Long jobId);
}
