package com.java_template.application.service;

import com.java_template.application.dto.TestRunCaseDTO;
import com.java_template.application.repository.TestRunCaseRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Run Case operations
 */
@Service
public class TestRunCaseService {
    private final TestRunCaseRepository testRunCaseRepository;

    public TestRunCaseService(TestRunCaseRepository testRunCaseRepository) {
        this.testRunCaseRepository = testRunCaseRepository;
    }

    /**
     * Creates a new test run case
     */
    public TestRunCaseDTO createTestRunCase(TestRunCaseDTO testRunCase) {
        testRunCase.setStatus("UNTESTED");
        return testRunCaseRepository.create(testRunCase);
    }

    /**
     * Retrieves a test run case by ID
     */
    public Optional<TestRunCaseDTO> getTestRunCaseById(UUID id) {
        return testRunCaseRepository.findById(id);
    }

    /**
     * Retrieves all test run cases for a specific test run
     */
    public List<TestRunCaseDTO> getTestRunCasesByTestRunId(UUID testRunId) {
        return testRunCaseRepository.findByTestRunId(testRunId);
    }

    /**
     * Updates the status of a test run case (UNTESTED, PASSED, FAILED, SKIPPED)
     */
    public Optional<TestRunCaseDTO> updateTestRunCaseStatus(UUID id, String status) {
        Optional<TestRunCaseDTO> testRunCase = testRunCaseRepository.findById(id);
        if (testRunCase.isPresent()) {
            TestRunCaseDTO trc = testRunCase.get();
            trc.setStatus(status);
            testRunCaseRepository.update(id, trc);
            return Optional.of(trc);
        }
        return Optional.empty();
    }
}

