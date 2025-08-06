package com.java_template.application.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SnapshotJobTest {

    @Test
    public void testSnapshotJobWithFailReason() {
        // Arrange
        SnapshotJob job = new SnapshotJob();
        job.setSeason("2023");
        job.setDateRangeStart("2023-08-01");
        job.setDateRangeEnd("2024-05-31");
        job.setStatus("FAILED");
        job.setCreatedAt("2023-09-01T12:00:00Z");
        job.setFailReason("Test failure reason");

        // Act & Assert
        assertEquals("2023", job.getSeason());
        assertEquals("2023-08-01", job.getDateRangeStart());
        assertEquals("2024-05-31", job.getDateRangeEnd());
        assertEquals("FAILED", job.getStatus());
        assertEquals("2023-09-01T12:00:00Z", job.getCreatedAt());
        assertEquals("Test failure reason", job.getFailReason());
    }

    @Test
    public void testSnapshotJobWithoutFailReason() {
        // Arrange
        SnapshotJob job = new SnapshotJob();
        job.setSeason("2023");
        job.setDateRangeStart("2023-08-01");
        job.setDateRangeEnd("2024-05-31");
        job.setStatus("COMPLETED");
        job.setCreatedAt("2023-09-01T12:00:00Z");

        // Act & Assert
        assertEquals("2023", job.getSeason());
        assertEquals("2023-08-01", job.getDateRangeStart());
        assertEquals("2024-05-31", job.getDateRangeEnd());
        assertEquals("COMPLETED", job.getStatus());
        assertEquals("2023-09-01T12:00:00Z", job.getCreatedAt());
        assertNull(job.getFailReason()); // Should be null for successful jobs
    }

    @Test
    public void testSnapshotJobValidation() {
        // Test valid job
        SnapshotJob validJob = new SnapshotJob();
        validJob.setSeason("2023");
        validJob.setDateRangeStart("2023-08-01");
        validJob.setDateRangeEnd("2024-05-31");
        validJob.setStatus("PENDING");

        assertTrue(validJob.isValid());

        // Test invalid job (missing season)
        SnapshotJob invalidJob = new SnapshotJob();
        invalidJob.setDateRangeStart("2023-08-01");
        invalidJob.setDateRangeEnd("2024-05-31");
        invalidJob.setStatus("PENDING");

        assertFalse(invalidJob.isValid());
    }
}
