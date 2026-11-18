package com.cognizant.backend.repository;

import java.time.Instant;
import java.util.Collection;
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

    /** Verdict counts per run and language, for run summaries; loads no answers. */
    @Query("""
            select new com.cognizant.backend.repository.VerdictTally(r.testRun.id, r.language, r.verdict,
                r.humanVerdict, r.auditSample, count(r))
            from TestResult r where r.testRun.id in :runIds
            group by r.testRun.id, r.language, r.verdict, r.humanVerdict, r.auditSample""")
    List<VerdictTally> tallyByRun(@Param("runIds") Collection<Long> runIds);

    long countByScenario_Id(Long scenarioId);

    @Query("select r.scenario.id, count(r) from TestResult r group by r.scenario.id")
    List<Object[]> countResultsPerScenario();

    /** A run's results waiting for a person: undecided by the checks, or picked for a spot-check. */
    @EntityGraph(attributePaths = {"scenario", "testRun"})
    @Query("""
            select r from TestResult r
            where r.testRun.id = :runId
              and r.humanVerdict is null
              and (r.verdict = com.cognizant.backend.entity.ResultVerdict.NEEDS_REVIEW or r.auditSample = true)
            """)
    List<TestResult> findReviewQueue(@Param("runId") Long runId);

    /** How many results wait for a person, per run: rows of [run id, count]. */
    @Query("""
            select r.testRun.id, count(r) from TestResult r
            where r.humanVerdict is null
              and (r.verdict = com.cognizant.backend.entity.ResultVerdict.NEEDS_REVIEW or r.auditSample = true)
            group by r.testRun.id
            """)
    List<Object[]> countReviewQueuePerRun();

    /** A run's answers picked for a person (unsure or spot-checked) that a person has already reviewed. */
    @Query("""
            select count(r) from TestResult r
            where r.testRun.id = :runId and r.humanVerdict is not null
              and (r.verdict = com.cognizant.backend.entity.ResultVerdict.NEEDS_REVIEW or r.auditSample = true)
            """)
    long countReviewedSelection(@Param("runId") Long runId);

    /** Reviewed results where the judge gave a definite verdict: the basis of the agreement rate. */
    @EntityGraph(attributePaths = {"testRun"})
    @Query("""
            select r from TestResult r
            where r.humanVerdict is not null
              and r.judgeVerdict in (com.cognizant.backend.entity.ResultVerdict.PASS,
                                     com.cognizant.backend.entity.ResultVerdict.FAIL)
            """)
    List<TestResult> findReviewedWithJudgeVerdict();

    /** A run's answers that a person has reviewed, most recent first. */
    @EntityGraph(attributePaths = {"scenario", "testRun"})
    @Query("select r from TestResult r where r.testRun.id = :runId and r.humanVerdict is not null order by r.reviewedAt desc")
    List<TestResult> findReviewed(@Param("runId") Long runId);

    /** When the run last recorded an answer; null if it has none yet. */
    @Query("select max(r.askedAt) from TestResult r where r.testRun.id = :runId")
    Instant findLastAskedAt(@Param("runId") Long runId);
}
