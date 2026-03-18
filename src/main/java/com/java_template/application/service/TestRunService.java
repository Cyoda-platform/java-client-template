package com.java_template.application.service;

import com.java_template.application.dto.TestRunDTO;
import com.java_template.application.repository.TestRunRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Run operations
 */
@Service
public class TestRunService {
    private final TestRunRepository testRunRepository;

    public TestRunService(TestRunRepository testRunRepository) {
        this.testRunRepository = testRunRepository;
    }

    /**
     * Creates a new test run with CREATED status
     */
    public TestRunDTO createTestRun(TestRunDTO testRun) {
        testRun.setStatus("CREATED");
        testRun.setStartedAt(LocalDateTime.now());
        return testRunRepository.create(testRun);
    }

    /**
     * Retrieves a test run by ID
     */
    public Optional<TestRunDTO> getTestRunById(UUID id) {
        return testRunRepository.findById(id);
    }

    /**
     * Retrieves all test runs for a specific project
     */
    public List<TestRunDTO> getTestRunsByProjectId(UUID projectId) {
        return testRunRepository.findByProjectId(projectId);
    }

    /**
     * Retrieves all test runs with a specific status
     */
    public List<TestRunDTO> getTestRunsByStatus(String status) {
        return testRunRepository.findByStatus(status);
    }

    /**
     * Retrieves all test runs
     */
    public List<TestRunDTO> getAllTestRuns() {
        return testRunRepository.findAll();
    }

    /**
     * Updates an existing test run
     */
    public TestRunDTO updateTestRun(UUID id, TestRunDTO testRun) {
        return testRunRepository.update(id, testRun);
    }

    /**
     * Completes a test run by setting status to COMPLETED and completedAt timestamp
     */
    public Optional<TestRunDTO> completeTestRun(UUID id) {
        Optional<TestRunDTO> testRun = testRunRepository.findById(id);
        if (testRun.isPresent()) {
            TestRunDTO run = testRun.get();
            run.setStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            testRunRepository.update(id, run);
            return Optional.of(run);
        }
        return Optional.empty();
    }

    /**
     * Unlocks a test run by setting status to ACTIVE
     */
    public Optional<TestRunDTO> unlockTestRun(UUID id) {
        Optional<TestRunDTO> testRun = testRunRepository.findById(id);
        if (testRun.isPresent()) {
            TestRunDTO run = testRun.get();
            run.setStatus("ACTIVE");
            testRunRepository.update(id, run);
            return Optional.of(run);
        }
        return Optional.empty();
    }

    /**
     * Checks if a test run exists by ID
     */
    public boolean testRunExists(UUID id) {
        return testRunRepository.exists(id);
    }

    /**
     * Deletes a test run by ID
     */
    public boolean deleteTestRun(UUID id) {
        return testRunRepository.delete(id);
    }
}

