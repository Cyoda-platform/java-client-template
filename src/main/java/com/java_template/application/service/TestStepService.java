package com.java_template.application.service;

import com.java_template.application.dto.TestStepDTO;
import com.java_template.application.repository.TestStepRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Step operations
 */
@Service
public class TestStepService {
    private final TestStepRepository testStepRepository;

    public TestStepService(TestStepRepository testStepRepository) {
        this.testStepRepository = testStepRepository;
    }

    /**
     * Creates a new test step
     */
    public TestStepDTO createTestStep(TestStepDTO testStep) {
        return testStepRepository.create(testStep);
    }

    /**
     * Retrieves a test step by ID
     */
    public Optional<TestStepDTO> getTestStepById(UUID id) {
        return testStepRepository.findById(id);
    }

    /**
     * Retrieves all test steps for a specific test case
     */
    public List<TestStepDTO> getTestStepsByTestCaseId(UUID testCaseId) {
        return testStepRepository.findByTestCaseId(testCaseId);
    }

    /**
     * Updates an existing test step
     */
    public TestStepDTO updateTestStep(UUID id, TestStepDTO testStep) {
        return testStepRepository.update(id, testStep);
    }

    /**
     * Deletes a test step by ID
     */
    public boolean deleteTestStep(UUID id) {
        return testStepRepository.delete(id);
    }
}

