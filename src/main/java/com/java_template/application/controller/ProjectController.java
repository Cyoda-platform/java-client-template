package com.java_template.application.controller;

import com.java_template.application.dto.ProjectDTO;
import com.java_template.application.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Project operations
 */
@RestController
@RequestMapping("/projects")
@Tag(name = "Projects", description = "Project management endpoints")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ProjectDTO> createProject(@RequestBody ProjectDTO project) {
        ProjectDTO created = projectService.createProject(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all projects")
    public ResponseEntity<List<ProjectDTO>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable UUID id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ProjectDTO> updateProject(@PathVariable UUID id, @RequestBody ProjectDTO project) {
        if (!projectService.projectExists(id)) {
            return ResponseEntity.notFound().build();
        }
        ProjectDTO updated = projectService.updateProject(id, project);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        if (projectService.deleteProject(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search projects by query parameter")
    public ResponseEntity<List<ProjectDTO>> searchProjects(@RequestParam String query) {
        // Filter projects by name or description containing query
        List<ProjectDTO> allProjects = projectService.getAllProjects();
        List<ProjectDTO> filtered = allProjects.stream()
                .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase()) ||
                        (p.getDescription() != null && p.getDescription().toLowerCase().contains(query.toLowerCase())))
                .toList();
        return ResponseEntity.ok(filtered);
    }
}

