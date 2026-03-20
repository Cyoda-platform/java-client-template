package com.java_template.application.repository;

import com.java_template.application.dto.TestRunCaseDTO;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Test Run Cases
 */
@Repository
public class TestRunCaseRepository {
    private final Map<UUID, TestRunCaseDTO> testRunCases = new ConcurrentHashMap<>();

    public TestRunCaseDTO create(TestRunCaseDTO testRunCase) {
        UUID id = UUID.randomUUID();
        testRunCase.setId(id);
        testRunCases.put(id, testRunCase);
        return testRunCase;
    }

    public Optional<TestRunCaseDTO> findById(UUID id) {
        return Optional.ofNullable(testRunCases.get(id));
    }

    public List<TestRunCaseDTO> findByTestRunId(UUID testRunId) {
        return testRunCases.values().stream()
                .filter(trc -> trc.getTestRunId().equals(testRunId))
                .collect(Collectors.toList());
    }

    public List<TestRunCaseDTO> findByTestCaseId(UUID testCaseId) {
        return testRunCases.values().stream()
                .filter(trc -> trc.getTestCaseId().equals(testCaseId))
                .collect(Collectors.toList());
    }

    public List<TestRunCaseDTO> findAll() {
        return new ArrayList<>(testRunCases.values());
    }

    public TestRunCaseDTO update(UUID id, TestRunCaseDTO testRunCase) {
        return testRunCases.computeIfPresent(id, (k, existing) -> {
            // Merge: update only non-null fields
            if (testRunCase.getStatus() != null) {
                existing.setStatus(testRunCase.getStatus());
            }
            if (testRunCase.getTestCaseId() != null) {
                existing.setTestCaseId(testRunCase.getTestCaseId());
            }
            if (testRunCase.getTestRunId() != null) {
                existing.setTestRunId(testRunCase.getTestRunId());
            }
            return existing;
        });
    }

    public boolean delete(UUID id) {
        return testRunCases.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return testRunCases.containsKey(id);
    }
}

