package com.cognizant.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.ScenarioFix;

public interface ScenarioFixRepository extends JpaRepository<ScenarioFix, Long> {

    Optional<ScenarioFix> findByScenario_IdAndLanguage(Long scenarioId, Language language);

    List<ScenarioFix> findByActiveTrue();

    @EntityGraph(attributePaths = {"scenario"})
    List<ScenarioFix> findAllByOrderByUpdatedAtDesc();

    List<ScenarioFix> findBySourceResultId(Long resultId);

    /** Counts one answer given with the fix, in one statement so answers saved at once never lose a count. */
    @Modifying
    @Query("""
            update ScenarioFix f set f.timesUsed = f.timesUsed + 1, f.timesPassed = f.timesPassed + :passed,
                f.lastOutcome = :outcome, f.lastUsedAt = :at
            where f.id = :id and f.revision = :revision""")
    int recordUse(@Param("id") Long id, @Param("revision") int revision, @Param("passed") int passed,
            @Param("outcome") ResultVerdict outcome, @Param("at") Instant at);
}
