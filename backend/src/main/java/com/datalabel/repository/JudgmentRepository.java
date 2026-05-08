package com.datalabel.repository;

import com.datalabel.domain.Judgment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JudgmentRepository extends JpaRepository<Judgment, Long> {

    Optional<Judgment> findByUnitIdAndUserId(Long unitId, Long userId);

    List<Judgment> findByUnitId(Long unitId);

    long countByUnitId(Long unitId);

    /** Hard-delete every judgment on a unit. Used by the EDIT-payload concern resolution path. */
    void deleteByUnitId(Long unitId);

    List<Judgment> findByUserIdAndWasGoldTrue(Long userId);

    /** Per-job gold judgments for a user (used for trust review). */
    List<Judgment> findByUserIdAndWasGoldTrueAndUnitIdIn(Long userId, List<Long> unitIds);

    /** All judgments by a user across a single job — used by the revert flow. */
    @Query("""
            select j from Judgment j
             where j.userId = :userId
               and j.unitId in (select u.id from Unit u where u.jobId = :jobId)
            """)
    List<Judgment> findByJobIdAndUserId(@Param("jobId") Long jobId, @Param("userId") Long userId);

    /** All judgments across a job — used by monitor stats and bulk operations. */
    @Query("""
            select j from Judgment j
             where j.unitId in (select u.id from Unit u where u.jobId = :jobId)
            """)
    List<Judgment> findByJobId(@Param("jobId") Long jobId);

    /** All gold judgments across a job — used to build the per-answer breakdown for admin views. */
    @Query("""
            select j from Judgment j
             where j.wasGold = true
               and j.unitId in (select u.id from Unit u where u.jobId = :jobId)
            """)
    List<Judgment> findGoldByJobId(@Param("jobId") Long jobId);

    /**
     * Per-unit gold-judgment outcomes for a job. Returns rows of
     * {@code [unit_id, matches, mismatches]} where matches/mismatches are
     * counts of judgments where {@code gold_correct} is true / false.
     * Only gold judgments are counted ({@code was_gold = true}).
     */
    @Query(value = """
            select j.unit_id,
                   sum(case when j.gold_correct then 1 else 0 end) as matches,
                   sum(case when j.gold_correct = false then 1 else 0 end) as mismatches
              from judgment j
              join unit u on u.id = j.unit_id
             where u.job_id = :jobId
               and j.was_gold = true
             group by j.unit_id
            """, nativeQuery = true)
    List<Object[]> aggregateGoldOutcomesForJob(@Param("jobId") Long jobId);
}
