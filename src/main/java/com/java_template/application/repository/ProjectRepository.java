package com.java_template.application.repository;

import com.java_template.application.dto.ProjectDTO;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for Projects
 */
@Repository
public class ProjectRepository {
    private final Map<UUID, ProjectDTO> projects = new ConcurrentHashMap<>();

    public ProjectDTO create(ProjectDTO project) {
        UUID id = UUID.randomUUID();
        project.setId(id);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projects.put(id, project);
        return project;
    }

    public Optional<ProjectDTO> findById(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    public List<ProjectDTO> findAll() {
        return new ArrayList<>(projects.values());
    }

    public ProjectDTO update(UUID id, ProjectDTO project) {
        project.setId(id);
        project.setUpdatedAt(LocalDateTime.now());
        projects.put(id, project);
        return project;
    }

    public boolean delete(UUID id) {
        return projects.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return projects.containsKey(id);
    }
}

