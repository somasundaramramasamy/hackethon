package com.cognizant.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cognizant.backend.entity.Investigation;
import com.cognizant.backend.entity.InvestigationStatus;

public interface InvestigationRepository extends JpaRepository<Investigation, Long> {

    List<Investigation> findByResult_IdOrderByStartedAtDesc(Long resultId);

    @EntityGraph(attributePaths = {"result", "result.scenario"})
    List<Investigation> findByResult_TestRun_IdAndStatusOrderByStartedAtAsc(Long runId, InvestigationStatus status);

    List<Investigation> findByStatus(InvestigationStatus status);
}
