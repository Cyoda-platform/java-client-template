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
        testRun.setId(id);
        testRun.setUpdatedAt(LocalDateTime.now());
        testRuns.put(id, testRun);
        return testRun;
    }

    public boolean delete(UUID id) {
        return testRuns.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return testRuns.containsKey(id);
    }
}

