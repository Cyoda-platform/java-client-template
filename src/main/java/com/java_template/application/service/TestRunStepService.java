package com.java_template.application.service;

import com.java_template.application.dto.TestRunStepDTO;
import com.java_template.application.repository.TestRunStepRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Run Step operations
 */
@Service
public class TestRunStepService {
    private final TestRunStepRepository testRunStepRepository;

    public TestRunStepService(TestRunStepRepository testRunStepRepository) {
        this.testRunStepRepository = testRunStepRepository;
    }

    /**
     * Creates a new test run step
     */
    public TestRunStepDTO createTestRunStep(TestRunStepDTO testRunStep) {
        testRunStep.setStatus("UNTESTED");
        return testRunStepRepository.create(testRunStep);
    }

    /**
     * Retrieves a test run step by ID
     */
    public Optional<TestRunStepDTO> getTestRunStepById(UUID id) {
        return testRunStepRepository.findById(id);
    }

    /**
     * Retrieves all test run steps for a specific test run case
     */
    public List<TestRunStepDTO> getTestRunStepsByTestRunCaseId(UUID testRunCaseId) {
        return testRunStepRepository.findByTestRunCaseId(testRunCaseId);
    }

    /**
     * Updates the status of a test run step (UNTESTED, PASSED, FAILED, SKIPPED)
     */
    public Optional<TestRunStepDTO> updateTestRunStepStatus(UUID id, String status) {
        Optional<TestRunStepDTO> testRunStep = testRunStepRepository.findById(id);
        if (testRunStep.isPresent()) {
            TestRunStepDTO trs = testRunStep.get();
            trs.setStatus(status);
            testRunStepRepository.update(id, trs);
            return Optional.of(trs);
        }
        return Optional.empty();
    }

    /**
     * Links a bug to a test run step
     */
    public Optional<TestRunStepDTO> linkBug(UUID id, String bugUrl) {
        Optional<TestRunStepDTO> testRunStep = testRunStepRepository.findById(id);
        if (testRunStep.isPresent()) {
            TestRunStepDTO trs = testRunStep.get();
            // Store bug URL in actualResult or create a dedicated field if needed
            testRunStepRepository.update(id, trs);
            return Optional.of(trs);
        }
        return Optional.empty();
    }
}

