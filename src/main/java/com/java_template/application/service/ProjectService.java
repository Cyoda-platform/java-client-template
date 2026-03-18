package com.java_template.application.service;

import com.java_template.application.dto.ProjectDTO;
import com.java_template.application.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Project operations
 */
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Creates a new project with ACTIVE status
     */
    public ProjectDTO createProject(ProjectDTO project) {
        project.setStatus("ACTIVE");
        return projectRepository.create(project);
    }

    /**
     * Retrieves a project by ID
     */
    public Optional<ProjectDTO> getProjectById(UUID id) {
        return projectRepository.findById(id);
    }

    /**
     * Retrieves all projects
     */
    public List<ProjectDTO> getAllProjects() {
        return projectRepository.findAll();
    }

    /**
     * Updates an existing project
     */
    public ProjectDTO updateProject(UUID id, ProjectDTO project) {
        return projectRepository.update(id, project);
    }

    /**
     * Deletes a project by ID
     */
    public boolean deleteProject(UUID id) {
        return projectRepository.delete(id);
    }

    /**
     * Searches projects by name (case-insensitive)
     */
    public List<ProjectDTO> searchProjects(String query) {
        return projectRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a project exists by ID
     */
    public boolean projectExists(UUID id) {
        return projectRepository.exists(id);
    }
}

