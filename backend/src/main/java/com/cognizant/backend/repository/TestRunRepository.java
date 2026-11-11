package com.cognizant.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cognizant.backend.entity.TestRun;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    List<TestRun> findAllByOrderByStartedAtDesc();

    Optional<TestRun> findByCode(String code);
}
