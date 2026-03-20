package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.TestCaseDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TestCaseService
 */
@ExtendWith(MockitoExtension.class)
public class TestCaseServiceTest {

    @Mock
    private EntityService entityService;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private TestCaseService testCaseService;

    private TestCaseDTO testCase;
    private UUID caseId;
    private UUID suiteId;

    private EntityWithMetadata<TestCaseDTO> entityWithMetadata(TestCaseDTO dto, UUID id) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(id);
        return new EntityWithMetadata<>(dto, metadata);
    }

    @BeforeEach
    public void setUp() {
        caseId = UUID.randomUUID();
        suiteId = UUID.randomUUID();
        testCase = new TestCaseDTO();
        testCase.setSuiteId(suiteId);
        testCase.setName("Test Case 1");
        testCase.setDescription("A test case");
    }

    @Test
    public void testCreateTestCase() {
        when(entityService.create(any(TestCaseDTO.class)))
                .thenAnswer(inv -> entityWithMetadata(inv.getArgument(0), caseId));

        TestCaseDTO created = testCaseService.createTestCase(testCase);

        assertNotNull(created.getId());
        assertEquals("Test Case 1", created.getName());
        assertEquals("ACTIVE", created.getStatus());
        assertFalse(created.isDeleted());
    }

    @Test
    public void testGetTestCaseById() {
        testCase.setId(caseId);
        when(entityService.getById(eq(caseId), any(), eq(TestCaseDTO.class)))
                .thenReturn(entityWithMetadata(testCase, caseId));

        Optional<TestCaseDTO> retrieved = testCaseService.getTestCaseById(caseId);

        assertTrue(retrieved.isPresent());
        assertEquals(caseId, retrieved.get().getId());
    }

    @Test
    public void testSoftDeleteTestCase() {
        testCase.setId(caseId);
        when(entityService.getById(eq(caseId), any(), eq(TestCaseDTO.class)))
                .thenReturn(entityWithMetadata(testCase, caseId))
                .thenThrow(new RuntimeException("Not found"));
        when(entityService.update(eq(caseId), any(TestCaseDTO.class), isNull()))
                .thenReturn(entityWithMetadata(testCase, caseId));

        boolean deleted = testCaseService.softDeleteTestCase(caseId);

        assertTrue(deleted);
        verify(entityService).update(eq(caseId), argThat((TestCaseDTO tc) -> tc.isDeleted()), isNull());
    }
}

