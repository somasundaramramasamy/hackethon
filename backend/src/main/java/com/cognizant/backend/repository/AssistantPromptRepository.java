package com.cognizant.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.cognizant.backend.entity.AssistantPrompt;
import com.cognizant.backend.entity.PromptStatus;

public interface AssistantPromptRepository extends JpaRepository<AssistantPrompt, Long> {

    Optional<AssistantPrompt> findByVersion(int version);

    Optional<AssistantPrompt> findFirstByStatus(PromptStatus status);

    List<AssistantPrompt> findAllByOrderByVersionDesc();

    @Query("select coalesce(max(p.version), 0) from AssistantPrompt p")
    int findMaxVersion();
}
