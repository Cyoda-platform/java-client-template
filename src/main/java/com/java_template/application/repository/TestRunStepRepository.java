package com.java_template.application.repository;

import com.java_template.application.dto.TestRunStepDTO;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Test Run Steps
 */
@Repository
public class TestRunStepRepository {
    private final Map<UUID, TestRunStepDTO> testRunSteps = new ConcurrentHashMap<>();

    public TestRunStepDTO create(TestRunStepDTO testRunStep) {
        UUID id = UUID.randomUUID();
        testRunStep.setId(id);
        testRunSteps.put(id, testRunStep);
        return testRunStep;
    }

    public Optional<TestRunStepDTO> findById(UUID id) {
        return Optional.ofNullable(testRunSteps.get(id));
    }

    public List<TestRunStepDTO> findByTestRunCaseId(UUID testRunCaseId) {
        return testRunSteps.values().stream()
                .filter(trs -> trs.getRunCaseId().equals(testRunCaseId))
                .collect(Collectors.toList());
    }

    public List<TestRunStepDTO> findAll() {
        return new ArrayList<>(testRunSteps.values());
    }

    public TestRunStepDTO update(UUID id, TestRunStepDTO testRunStep) {
        testRunStep.setId(id);
        testRunSteps.put(id, testRunStep);
        return testRunStep;
    }

    public boolean delete(UUID id) {
        return testRunSteps.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return testRunSteps.containsKey(id);
    }
}

