package com.cognizant.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.cognizant.backend.entity.TestScenario;

public interface TestScenarioRepository extends JpaRepository<TestScenario, Long> {

    List<TestScenario> findAllByOrderByPositionAsc();

    List<TestScenario> findAllByActiveTrueOrderByPositionAsc();

    Optional<TestScenario> findByCode(String code);

    @Query("select coalesce(max(s.position), 0) from TestScenario s")
    int findMaxPosition();

    @Query("select s.code from TestScenario s")
    List<String> findAllCodes();
}
