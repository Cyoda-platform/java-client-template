package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.ProjectDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectService
 */
@ExtendWith(MockitoExtension.class)
public class ProjectServiceTest {

    @Mock
    private EntityService entityService;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProjectService projectService;

    private ProjectDTO testProject;
    private UUID projectId;

    private EntityWithMetadata<ProjectDTO> entityWithMetadata(ProjectDTO dto, UUID id) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(id);
        return new EntityWithMetadata<>(dto, metadata);
    }

    @BeforeEach
    public void setUp() {
        projectId = UUID.randomUUID();
        testProject = new ProjectDTO();
        testProject.setName("Test Project");
        testProject.setDescription("A test project");
    }

    @Test
    public void testCreateProject() {
        when(entityService.create(any(ProjectDTO.class)))
                .thenAnswer(inv -> entityWithMetadata(inv.getArgument(0), projectId));

        ProjectDTO created = projectService.createProject(testProject);

        assertNotNull(created.getId());
        assertEquals(projectId, created.getId());
        assertEquals("Test Project", created.getName());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    public void testGetProjectById() {
        testProject.setId(projectId);
        when(entityService.getById(eq(projectId), any(), eq(ProjectDTO.class)))
                .thenReturn(entityWithMetadata(testProject, projectId));

        Optional<ProjectDTO> retrieved = projectService.getProjectById(projectId);

        assertTrue(retrieved.isPresent());
        assertEquals(projectId, retrieved.get().getId());
    }

    @Test
    public void testGetProjectById_notFound() {
        when(entityService.getById(any(), any(), eq(ProjectDTO.class)))
                .thenThrow(new RuntimeException("Not found"));

        Optional<ProjectDTO> retrieved = projectService.getProjectById(projectId);

        assertFalse(retrieved.isPresent());
    }

    @Test
    public void testGetAllProjects() {
        PageResult<EntityWithMetadata<ProjectDTO>> page =
                PageResult.of(null, List.of(entityWithMetadata(testProject, projectId)), 0, 20, 1);
        when(entityService.findAll(any(), eq(ProjectDTO.class), any())).thenReturn(page);

        var result = projectService.getAllProjects(0, 20);

        assertFalse(result.data().isEmpty());
        assertEquals(1, result.data().size());
    }

    @Test
    public void testDeleteProject() {
        when(entityService.deleteById(projectId)).thenReturn(projectId);

        boolean deleted = projectService.deleteProject(projectId);

        assertTrue(deleted);
        verify(entityService).deleteById(projectId);
    }
}

