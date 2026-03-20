package com.java_template.application.repository;

import com.java_template.application.dto.TestRunDTO;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Test Runs
 */
@Repository
public class TestRunRepository {
    private final Map<UUID, TestRunDTO> testRuns = new ConcurrentHashMap<>();

    public TestRunDTO create(TestRunDTO testRun) {
        UUID id = UUID.randomUUID();
        testRun.setId(id);
        testRun.setCreatedAt(LocalDateTime.now());
        testRun.setUpdatedAt(LocalDateTime.now());
        testRuns.put(id, testRun);
        return testRun;
    }

    public Optional<TestRunDTO> findById(UUID id) {
        return Optional.ofNullable(testRuns.get(id));
    }

    public List<TestRunDTO> findByProjectId(UUID projectId) {
        return testRuns.values().stream()
                .filter(tr -> tr.getProjectId().equals(projectId))
                .collect(Collectors.toList());
    }

    public List<TestRunDTO> findByStatus(String status) {
        return testRuns.values().stream()
                .filter(tr -> tr.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    public List<TestRunDTO> findAll() {
        return new ArrayList<>(testRuns.values());
    }

    public TestRunDTO update(UUID id, TestRunDTO testRun) {
        return testRuns.computeIfPresent(id, (k, existing) -> {
            // Merge: update only non-null fields
            if (testRun.getTitle() != null) {
                existing.setTitle(testRun.getTitle());
            }
            if (testRun.getEnvironment() != null) {
                existing.setEnvironment(testRun.getEnvironment());
            }
            if (testRun.getStatus() != null) {
                existing.setStatus(testRun.getStatus());
            }
            if (testRun.getStartedAt() != null) {
                existing.setStartedAt(testRun.getStartedAt());
            }
            if (testRun.getCompletedAt() != null) {
                existing.setCompletedAt(testRun.getCompletedAt());
            }
            if (testRun.getProjectId() != null) {
                existing.setProjectId(testRun.getProjectId());
            }
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        });
    }

    public boolean delete(UUID id) {
        return testRuns.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return testRuns.containsKey(id);
    }
}

