package com.cognizant.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cognizant.backend.entity.HumanReview;

public interface HumanReviewRepository extends JpaRepository<HumanReview, Long> {

    List<HumanReview> findByReviewCase_CodeOrderByCreatedAtDesc(String reviewCaseCode);
}
