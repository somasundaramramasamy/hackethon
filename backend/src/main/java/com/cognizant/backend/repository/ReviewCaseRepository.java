package com.cognizant.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cognizant.backend.entity.ReviewCase;
import com.cognizant.backend.entity.ReviewStatus;

public interface ReviewCaseRepository extends JpaRepository<ReviewCase, Long> {

    Optional<ReviewCase> findByCode(String code);

    long countByReviewStatus(ReviewStatus status);
}
