package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.TestRunDTO;
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
 * Unit tests for TestRunService
 */
@ExtendWith(MockitoExtension.class)
public class TestRunServiceTest {

    @Mock
    private EntityService entityService;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private TestRunService testRunService;

    private TestRunDTO testRun;
    private UUID runId;
    private UUID projectId;

    private EntityWithMetadata<TestRunDTO> entityWithMetadata(TestRunDTO dto, UUID id) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(id);
        return new EntityWithMetadata<>(dto, metadata);
    }

    @BeforeEach
    public void setUp() {
        runId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        testRun = new TestRunDTO();
        testRun.setProjectId(projectId);
        testRun.setTitle("Test Run 1");
        testRun.setEnvironment("STAGING");
    }

    @Test
    public void testCreateTestRun() {
        when(entityService.create(any(TestRunDTO.class)))
                .thenAnswer(inv -> entityWithMetadata(inv.getArgument(0), runId));
        when(entityService.update(eq(runId), any(TestRunDTO.class), eq("initialize_run")))
                .thenAnswer(inv -> entityWithMetadata(inv.getArgument(1), runId));

        TestRunDTO created = testRunService.createTestRun(testRun);

        assertNotNull(created.getId());
        assertEquals("Test Run 1", created.getTitle());
        assertNotNull(created.getStartedAt());
    }

    @Test
    public void testGetTestRunById() {
        testRun.setId(runId);
        when(entityService.getById(eq(runId), any(), eq(TestRunDTO.class)))
                .thenReturn(entityWithMetadata(testRun, runId));

        Optional<TestRunDTO> retrieved = testRunService.getTestRunById(runId);

        assertTrue(retrieved.isPresent());
        assertEquals(runId, retrieved.get().getId());
    }

    @Test
    public void testGetTestRunsByProjectId() {
        PageResult<EntityWithMetadata<TestRunDTO>> page =
                PageResult.of(null, List.of(entityWithMetadata(testRun, runId)), 0, 20, 1);
        when(entityService.search(any(), any(), eq(TestRunDTO.class), any())).thenReturn(page);

        var result = testRunService.getTestRunsByProjectId(projectId, 0, 20);

        assertFalse(result.data().isEmpty());
        assertEquals(1, result.data().size());
    }

    @Test
    public void testDeleteTestRun() {
        when(entityService.deleteById(runId)).thenReturn(runId);

        boolean deleted = testRunService.deleteTestRun(runId);

        assertTrue(deleted);
        verify(entityService).deleteById(runId);
    }
}

