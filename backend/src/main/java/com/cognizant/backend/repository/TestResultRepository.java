package com.cognizant.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.cognizant.backend.entity.TestResult;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByTestRun_IdOrderByIdAsc(Long testRunId);

    long countByScenario_Id(Long scenarioId);

    /** [scenarioId, resultCount] for every scenario that has results. */
    @Query("select r.scenario.id, count(r) from TestResult r group by r.scenario.id")
    List<Object[]> countResultsPerScenario();
}
