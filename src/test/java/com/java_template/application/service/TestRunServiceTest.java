package com.java_template.application.service;

import com.java_template.application.dto.TestRunDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestRunService
 */
@SpringBootTest(properties = {
    "app.auth.filter.enabled=false",
    "app.config.cyoda-client-id=test-client",
    "app.config.cyoda-client-secret=test-secret",
    "app.config.cyoda-host=localhost",
    "app.config.cyoda-api-url=http://localhost:8080/api",
    "app.config.grpc-address=localhost",
    "app.config.grpc-server-port=50051"
})
public class TestRunServiceTest {
    @Autowired
    private TestRunService testRunService;

    private TestRunDTO testRun;
    private UUID projectId;

    @BeforeEach
    public void setUp() {
        projectId = UUID.randomUUID();
        testRun = new TestRunDTO();
        testRun.setProjectId(projectId);
        testRun.setName("Test Run 1");
    }

    @Test
    public void testCreateTestRun() {
        TestRunDTO created = testRunService.createTestRun(testRun);
        assertNotNull(created.getId());
        assertEquals("Test Run 1", created.getName());
        assertEquals("CREATED", created.getStatus());
        assertNotNull(created.getStartedAt());
    }

    @Test
    public void testGetTestRunById() {
        TestRunDTO created = testRunService.createTestRun(testRun);
        Optional<TestRunDTO> retrieved = testRunService.getTestRunById(created.getId());
        
        assertTrue(retrieved.isPresent());
        assertEquals(created.getId(), retrieved.get().getId());
    }

    @Test
    public void testGetTestRunsByProjectId() {
        testRunService.createTestRun(testRun);
        var runs = testRunService.getTestRunsByProjectId(projectId);
        assertFalse(runs.isEmpty());
    }

    @Test
    public void testDeleteTestRun() {
        TestRunDTO created = testRunService.createTestRun(testRun);
        boolean deleted = testRunService.deleteTestRun(created.getId());
        assertTrue(deleted);
        assertFalse(testRunService.testRunExists(created.getId()));
    }
}

