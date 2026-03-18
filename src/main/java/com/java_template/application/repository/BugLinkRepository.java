package com.java_template.application.repository;

import com.java_template.application.dto.BugLinkDTO;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Bug Links
 */
@Repository
public class BugLinkRepository {
    private final Map<UUID, BugLinkDTO> bugLinks = new ConcurrentHashMap<>();

    public BugLinkDTO create(BugLinkDTO bugLink) {
        UUID id = UUID.randomUUID();
        bugLink.setId(id);
        bugLink.setCreatedAt(LocalDateTime.now());
        bugLinks.put(id, bugLink);
        return bugLink;
    }

    public Optional<BugLinkDTO> findById(UUID id) {
        return Optional.ofNullable(bugLinks.get(id));
    }

    public List<BugLinkDTO> findByTestRunStepId(UUID testRunStepId) {
        return bugLinks.values().stream()
                .filter(bl -> bl.getTestRunStepId().equals(testRunStepId))
                .collect(Collectors.toList());
    }

    public List<BugLinkDTO> findAll() {
        return new ArrayList<>(bugLinks.values());
    }

    public boolean delete(UUID id) {
        return bugLinks.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return bugLinks.containsKey(id);
    }
}

