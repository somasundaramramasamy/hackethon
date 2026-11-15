package com.cognizant.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestRun;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    List<TestRun> findAllByOrderByStartedAtDesc();

    Optional<TestRun> findByCode(String code);

    Optional<TestRun> findFirstByOrderByStartedAtDesc();

    Optional<TestRun> findFirstByTypeAndStatusOrderByStartedAtDesc(RunType type, RunStatus status);

    boolean existsByStatus(RunStatus status);

    List<TestRun> findByStatus(RunStatus status);

    List<TestRun> findByBaselineRunIdOrderByStartedAtDesc(Long baselineRunId);

    List<TestRun> findByTypeAndPromptVersionOrderByStartedAtDesc(RunType type, Integer promptVersion);
}
