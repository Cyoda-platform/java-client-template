package com.java_template.application.service;

import com.java_template.application.dto.TestCaseDTO;
import com.java_template.application.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Case operations
 */
@Service
public class TestCaseService {
    private final TestCaseRepository testCaseRepository;

    public TestCaseService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    public TestCaseDTO createTestCase(TestCaseDTO testCase) {
        testCase.setStatus("ACTIVE");
        testCase.setDeleted(false);
        return testCaseRepository.create(testCase);
    }

    public Optional<TestCaseDTO> getTestCaseById(UUID id) {
        return testCaseRepository.findById(id);
    }

    public List<TestCaseDTO> getTestCasesBySuiteId(UUID suiteId) {
        return testCaseRepository.findBySuiteId(suiteId);
    }

    public List<TestCaseDTO> getAllTestCases() {
        return testCaseRepository.findAll();
    }

    public TestCaseDTO updateTestCase(UUID id, TestCaseDTO testCase) {
        return testCaseRepository.update(id, testCase);
    }

    public boolean softDeleteTestCase(UUID id) {
        return testCaseRepository.softDelete(id);
    }

    public boolean testCaseExists(UUID id) {
        return testCaseRepository.exists(id);
    }
}

