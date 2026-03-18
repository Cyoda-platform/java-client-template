package com.java_template.application.service;

import com.java_template.application.dto.TestCaseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestCaseService
 */
@SpringBootTest
public class TestCaseServiceTest {
    @Autowired
    private TestCaseService testCaseService;

    private TestCaseDTO testCase;
    private UUID suiteId;

    @BeforeEach
    public void setUp() {
        suiteId = UUID.randomUUID();
        testCase = new TestCaseDTO();
        testCase.setSuiteId(suiteId);
        testCase.setName("Test Case 1");
        testCase.setDescription("A test case");
    }

    @Test
    public void testCreateTestCase() {
        TestCaseDTO created = testCaseService.createTestCase(testCase);
        assertNotNull(created.getId());
        assertEquals("Test Case 1", created.getName());
        assertEquals("ACTIVE", created.getStatus());
        assertFalse(created.isDeleted());
    }

    @Test
    public void testGetTestCaseById() {
        TestCaseDTO created = testCaseService.createTestCase(testCase);
        Optional<TestCaseDTO> retrieved = testCaseService.getTestCaseById(created.getId());
        
        assertTrue(retrieved.isPresent());
        assertEquals(created.getId(), retrieved.get().getId());
    }

    @Test
    public void testSoftDeleteTestCase() {
        TestCaseDTO created = testCaseService.createTestCase(testCase);
        boolean deleted = testCaseService.softDeleteTestCase(created.getId());
        assertTrue(deleted);
        assertFalse(testCaseService.testCaseExists(created.getId()));
    }
}

