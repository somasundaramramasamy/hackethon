package com.cognizant.backend.repository;

import java.util.Collection;
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

    /** Earlier rejected proposals for the same run and starting version, oldest first. */
    List<AssistantPrompt> findByStatusAndSourceRunIdAndBasedOnVersionOrderByVersionAsc(PromptStatus status,
            Long sourceRunId, Integer basedOnVersion);

    /** Earlier proposals for the same run and starting version in any of these states, oldest first. */
    List<AssistantPrompt> findBySourceRunIdAndBasedOnVersionAndStatusInOrderByVersionAsc(Long sourceRunId,
            Integer basedOnVersion, Collection<PromptStatus> statuses);

    @Query("select coalesce(max(p.version), 0) from AssistantPrompt p")
    int findMaxVersion();
}
