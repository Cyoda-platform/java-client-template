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

    public TestRunDTO createTestRun(TestRunDTO testRun) {
        testRun.setStatus("CREATED");
        testRun.setStartedAt(LocalDateTime.now());
        return testRunRepository.create(testRun);
    }

    public Optional<TestRunDTO> getTestRunById(UUID id) {
        return testRunRepository.findById(id);
    }

    public List<TestRunDTO> getTestRunsByProjectId(UUID projectId) {
        return testRunRepository.findByProjectId(projectId);
    }

    public List<TestRunDTO> getAllTestRuns() {
        return testRunRepository.findAll();
    }

    public TestRunDTO updateTestRun(UUID id, TestRunDTO testRun) {
        return testRunRepository.update(id, testRun);
    }

    public boolean deleteTestRun(UUID id) {
        return testRunRepository.delete(id);
    }

    public boolean testRunExists(UUID id) {
        return testRunRepository.exists(id);
    }
}

