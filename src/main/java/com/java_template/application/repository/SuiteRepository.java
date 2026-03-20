package com.java_template.application.repository;

import com.java_template.application.dto.SuiteDTO;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Test Suites
 */
@Repository
public class SuiteRepository {
    private final Map<UUID, SuiteDTO> suites = new ConcurrentHashMap<>();

    public SuiteDTO create(SuiteDTO suite) {
        UUID id = UUID.randomUUID();
        suite.setId(id);
        suite.setCreatedAt(LocalDateTime.now());
        suite.setUpdatedAt(LocalDateTime.now());
        suites.put(id, suite);
        return suite;
    }

    public Optional<SuiteDTO> findById(UUID id) {
        return Optional.ofNullable(suites.get(id));
    }

    public List<SuiteDTO> findByProjectId(UUID projectId) {
        return suites.values().stream()
                .filter(s -> s.getProjectId().equals(projectId))
                .collect(Collectors.toList());
    }

    public List<SuiteDTO> findAll() {
        return new ArrayList<>(suites.values());
    }

    public SuiteDTO update(UUID id, SuiteDTO suite) {
        return suites.computeIfPresent(id, (k, existing) -> {
            // Merge: update only non-null fields
            if (suite.getName() != null) {
                existing.setName(suite.getName());
            }
            if (suite.getDescription() != null) {
                existing.setDescription(suite.getDescription());
            }
            if (suite.getStatus() != null) {
                existing.setStatus(suite.getStatus());
            }
            if (suite.getProjectId() != null) {
                existing.setProjectId(suite.getProjectId());
            }
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        });
    }

    public boolean delete(UUID id) {
        return suites.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return suites.containsKey(id);
    }
}

