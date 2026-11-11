package com.cognizant.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.dto.RunSummaryResponse;
import com.cognizant.backend.repository.TestRunRepository;

@Service
@Transactional(readOnly = true)
public class TestRunService {

    private final TestRunRepository testRunRepository;

    public TestRunService(TestRunRepository testRunRepository) {
        this.testRunRepository = testRunRepository;
    }

    public List<RunSummaryResponse> listRuns() {
        return testRunRepository.findAllByOrderByStartedAtDesc().stream()
                .map(RunSummaryResponse::from)
                .toList();
    }
}
