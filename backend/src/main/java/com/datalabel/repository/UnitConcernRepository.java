package com.datalabel.repository;

import com.datalabel.domain.ConcernStatus;
import com.datalabel.domain.UnitConcern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UnitConcernRepository extends JpaRepository<UnitConcern, Long> {

    List<UnitConcern> findByUnitIdOrderByRaisedAtDesc(Long unitId);

    List<UnitConcern> findByStatusOrderByRaisedAtDesc(ConcernStatus status);

    long countByStatus(ConcernStatus status);

    /**
     * Open concerns across the units of a given job. Used by the per-job admin badge so we
     * don't have to walk every unit individually.
     */
    @Query("""
            select c from UnitConcern c
            where c.status = :status
              and c.unitId in (select u.id from Unit u where u.jobId = :jobId)
            order by c.raisedAt desc
            """)
    List<UnitConcern> findByJobIdAndStatus(@Param("jobId") Long jobId,
                                           @Param("status") ConcernStatus status);

    long countByUnitIdAndStatus(Long unitId, ConcernStatus status);
}
