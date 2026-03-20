package com.java_template.application.repository;

import com.java_template.application.dto.TestStepDTO;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Test Steps
 */
@Repository
public class TestStepRepository {
    private final Map<UUID, TestStepDTO> testSteps = new ConcurrentHashMap<>();

    public TestStepDTO create(TestStepDTO testStep) {
        UUID id = UUID.randomUUID();
        testStep.setId(id);
        testSteps.put(id, testStep);
        return testStep;
    }

    public Optional<TestStepDTO> findById(UUID id) {
        return Optional.ofNullable(testSteps.get(id));
    }

    public List<TestStepDTO> findByTestCaseId(UUID testCaseId) {
        return testSteps.values().stream()
                .filter(ts -> ts.getTestCaseId().equals(testCaseId))
                .collect(Collectors.toList());
    }

    public List<TestStepDTO> findAll() {
        return new ArrayList<>(testSteps.values());
    }

    public TestStepDTO update(UUID id, TestStepDTO testStep) {
        return testSteps.computeIfPresent(id, (k, existing) -> {
            // Merge: update only non-null fields
            if (testStep.getAction() != null) {
                existing.setAction(testStep.getAction());
            }
            if (testStep.getExpectedResult() != null) {
                existing.setExpectedResult(testStep.getExpectedResult());
            }
            if (testStep.getStatus() != null) {
                existing.setStatus(testStep.getStatus());
            }
            if (testStep.getStepNumber() != null) {
                existing.setStepNumber(testStep.getStepNumber());
            }
            if (testStep.getTestCaseId() != null) {
                existing.setTestCaseId(testStep.getTestCaseId());
            }
            return existing;
        });
    }

    public boolean delete(UUID id) {
        return testSteps.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return testSteps.containsKey(id);
    }
}

