package com.java_template.application.service;

import com.java_template.application.dto.ProjectDTO;
import com.java_template.application.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectService
 */
@SpringBootTest
public class ProjectServiceTest {
    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    private ProjectDTO testProject;

    @BeforeEach
    public void setUp() {
        testProject = new ProjectDTO();
        testProject.setName("Test Project");
        testProject.setDescription("A test project");
    }

    @Test
    public void testCreateProject() {
        ProjectDTO created = projectService.createProject(testProject);
        assertNotNull(created.getId());
        assertEquals("Test Project", created.getName());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    public void testGetProjectById() {
        ProjectDTO created = projectService.createProject(testProject);
        Optional<ProjectDTO> retrieved = projectService.getProjectById(created.getId());
        
        assertTrue(retrieved.isPresent());
        assertEquals(created.getId(), retrieved.get().getId());
    }

    @Test
    public void testGetAllProjects() {
        projectService.createProject(testProject);
        var projects = projectService.getAllProjects();
        assertFalse(projects.isEmpty());
    }

    @Test
    public void testDeleteProject() {
        ProjectDTO created = projectService.createProject(testProject);
        boolean deleted = projectService.deleteProject(created.getId());
        assertTrue(deleted);
        assertFalse(projectService.projectExists(created.getId()));
    }
}

