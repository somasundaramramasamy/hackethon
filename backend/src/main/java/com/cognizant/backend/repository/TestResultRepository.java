package com.cognizant.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cognizant.backend.entity.TestResult;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    @EntityGraph(attributePaths = {"scenario", "testRun"})
    List<TestResult> findByTestRun_IdOrderByIdAsc(Long testRunId);

    @EntityGraph(attributePaths = {"scenario", "testRun"})
    Optional<TestResult> findWithScenarioById(Long id);

    long countByTestRun_Id(Long testRunId);

    long countByScenario_Id(Long scenarioId);

    @Query("select r.scenario.id, count(r) from TestResult r group by r.scenario.id")
    List<Object[]> countResultsPerScenario();

    /** Results a person should look at: flagged, or picked for the audit, and not yet reviewed. */
    @EntityGraph(attributePaths = {"scenario", "testRun"})
    @Query("""
            select r from TestResult r
            where r.humanVerdict is null
              and (r.verdict in (com.cognizant.backend.entity.ResultVerdict.FAIL,
                                 com.cognizant.backend.entity.ResultVerdict.NEEDS_REVIEW)
                   or r.auditSample = true)
            order by r.testRun.startedAt desc, r.id asc
            """)
    List<TestResult> findReviewQueue();

    @Query("""
            select count(r) from TestResult r
            where r.humanVerdict is null
              and (r.verdict in (com.cognizant.backend.entity.ResultVerdict.FAIL,
                                 com.cognizant.backend.entity.ResultVerdict.NEEDS_REVIEW)
                   or r.auditSample = true)
            """)
    long countReviewQueue();

    /** Reviewed results where the judge gave a definite verdict: the basis of the agreement rate. */
    @EntityGraph(attributePaths = {"testRun"})
    @Query("""
            select r from TestResult r
            where r.humanVerdict is not null
              and r.judgeVerdict in (com.cognizant.backend.entity.ResultVerdict.PASS,
                                     com.cognizant.backend.entity.ResultVerdict.FAIL)
            """)
    List<TestResult> findReviewedWithJudgeVerdict();

    /** When the run last recorded an answer; null if it has none yet. */
    @Query("select max(r.askedAt) from TestResult r where r.testRun.id = :runId")
    Instant findLastAskedAt(@Param("runId") Long runId);
}
