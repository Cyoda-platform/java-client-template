package com.java_template.application.service;

import com.java_template.application.dto.ProjectDTO;
import com.java_template.application.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Project operations
 */
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectDTO createProject(ProjectDTO project) {
        project.setStatus("ACTIVE");
        return projectRepository.create(project);
    }

    public Optional<ProjectDTO> getProjectById(UUID id) {
        return projectRepository.findById(id);
    }

    public List<ProjectDTO> getAllProjects() {
        return projectRepository.findAll();
    }

    public ProjectDTO updateProject(UUID id, ProjectDTO project) {
        return projectRepository.update(id, project);
    }

    public boolean deleteProject(UUID id) {
        return projectRepository.delete(id);
    }

    public boolean projectExists(UUID id) {
        return projectRepository.exists(id);
    }
}

