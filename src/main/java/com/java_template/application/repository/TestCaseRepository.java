package com.java_template.application.repository;

import com.java_template.application.dto.TestCaseDTO;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Test Cases
 */
@Repository
public class TestCaseRepository {
    private final Map<UUID, TestCaseDTO> testCases = new ConcurrentHashMap<>();

    public TestCaseDTO create(TestCaseDTO testCase) {
        UUID id = UUID.randomUUID();
        testCase.setId(id);
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setUpdatedAt(LocalDateTime.now());
        testCase.setDeleted(false);
        testCases.put(id, testCase);
        return testCase;
    }

    public Optional<TestCaseDTO> findById(UUID id) {
        return Optional.ofNullable(testCases.get(id))
                .filter(tc -> !tc.isDeleted());
    }

    public List<TestCaseDTO> findBySuiteId(UUID suiteId) {
        return testCases.values().stream()
                .filter(tc -> tc.getSuiteId().equals(suiteId) && !tc.isDeleted())
                .collect(Collectors.toList());
    }

    public List<TestCaseDTO> findByProjectId(UUID projectId) {
        return testCases.values().stream()
                .filter(tc -> tc.getProjectId() != null && tc.getProjectId().equals(projectId) && !tc.isDeleted())
                .collect(Collectors.toList());
    }

    public List<TestCaseDTO> findAll() {
        return testCases.values().stream()
                .filter(tc -> !tc.isDeleted())
                .collect(Collectors.toList());
    }

    public TestCaseDTO update(UUID id, TestCaseDTO testCase) {
        return testCases.computeIfPresent(id, (k, existing) -> {
            // Merge: update only non-null fields
            if (testCase.getName() != null) {
                existing.setName(testCase.getName());
            }
            if (testCase.getDescription() != null) {
                existing.setDescription(testCase.getDescription());
            }
            if (testCase.getStatus() != null) {
                existing.setStatus(testCase.getStatus());
            }
            if (testCase.getProjectId() != null) {
                existing.setProjectId(testCase.getProjectId());
            }
            if (testCase.getSuiteId() != null) {
                existing.setSuiteId(testCase.getSuiteId());
            }
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        });
    }

    public boolean softDelete(UUID id) {
        return testCases.computeIfPresent(id, (k, v) -> {
            v.setDeleted(true);
            v.setUpdatedAt(LocalDateTime.now());
            return v;
        }) != null;
    }

    public boolean exists(UUID id) {
        return testCases.containsKey(id) && !testCases.get(id).isDeleted();
    }
}

