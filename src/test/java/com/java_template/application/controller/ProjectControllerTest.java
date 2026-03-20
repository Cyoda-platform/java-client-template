package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.ProjectDTO;
import com.java_template.application.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.java_template.common.dto.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ProjectController
 */
@WebMvcTest(controllers = ProjectController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
public class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @Test
    public void testCreateProject() throws Exception {
        ProjectDTO project = new ProjectDTO();
        project.setName("Test Project");
        project.setDescription("A test project");

        ProjectDTO created = new ProjectDTO();
        created.setId(UUID.randomUUID());
        created.setName("Test Project");
        created.setStatus("ACTIVE");

        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(created);

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Project"));
    }

    @Test
    public void testGetAllProjects() throws Exception {
        PageResult<ProjectDTO> emptyPage = PageResult.of(null, List.of(), 0, 20, 0);
        when(projectService.getAllProjects(0, 20)).thenReturn(emptyPage);

        mockMvc.perform(get("/projects")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void testGetProjectNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(projectService.getProjectById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/projects/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}

