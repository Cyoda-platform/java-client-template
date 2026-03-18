package com.java_template.application.service;

import com.java_template.application.dto.TestCaseDTO;
import com.java_template.application.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Test Case operations
 */
@Service
public class TestCaseService {
    private final TestCaseRepository testCaseRepository;

    public TestCaseService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * Creates a new test case with ACTIVE status
     */
    public TestCaseDTO createTestCase(TestCaseDTO testCase) {
        testCase.setStatus("ACTIVE");
        testCase.setDeleted(false);
        return testCaseRepository.create(testCase);
    }

    /**
     * Retrieves a test case by ID
     */
    public Optional<TestCaseDTO> getTestCaseById(UUID id) {
        return testCaseRepository.findById(id);
    }

    /**
     * Retrieves all test cases for a specific suite
     */
    public List<TestCaseDTO> getTestCasesBySuiteId(UUID suiteId) {
        return testCaseRepository.findBySuiteId(suiteId);
    }

    /**
     * Retrieves all test cases
     */
    public List<TestCaseDTO> getAllTestCases() {
        return testCaseRepository.findAll();
    }

    /**
     * Updates an existing test case
     */
    public TestCaseDTO updateTestCase(UUID id, TestCaseDTO testCase) {
        return testCaseRepository.update(id, testCase);
    }

    /**
     * Soft deletes a test case by ID
     */
    public boolean deleteTestCase(UUID id) {
        return testCaseRepository.softDelete(id);
    }

    /**
     * Searches test cases by name or description (case-insensitive)
     */
    public List<TestCaseDTO> searchTestCases(String query) {
        return testCaseRepository.findAll().stream()
                .filter(tc -> (tc.getName() != null && tc.getName().toLowerCase().contains(query.toLowerCase())) ||
                             (tc.getDescription() != null && tc.getDescription().toLowerCase().contains(query.toLowerCase())))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a test case exists by ID
     */
    public boolean testCaseExists(UUID id) {
        return testCaseRepository.exists(id);
    }

    /**
     * Soft deletes a test case by ID
     */
    public boolean softDeleteTestCase(UUID id) {
        return testCaseRepository.softDelete(id);
    }
}

