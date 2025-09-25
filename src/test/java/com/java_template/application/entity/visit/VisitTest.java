package com.java_template.application.entity.visit;

import com.java_template.application.entity.visit.version_1.Visit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Visit entity validation and business logic
 */
class VisitTest {

    private Visit visit;

    @BeforeEach
    void setUp() {
        visit = new Visit();
        visit.setVisitId("VISIT-001");
        visit.setSubjectId("SUBJ-001");
        visit.setStudyId("STUDY-001");
        visit.setVisitCode("V1");
        visit.setStatus("planned");
        visit.setPlannedDate(LocalDate.now().plusDays(7));
        visit.setLocked(false);
        visit.setWindowMinusDays(3);
        visit.setWindowPlusDays(3);
    }

    @Test
    @DisplayName("Valid visit should pass validation")
    void testValidVisit() {
        assertTrue(visit.isValid());
    }

    @Test
    @DisplayName("Visit with null visitId should fail validation")
    void testInvalidVisitId() {
        visit.setVisitId(null);
        assertFalse(visit.isValid());
        
        visit.setVisitId("");
        assertFalse(visit.isValid());
        
        visit.setVisitId("   ");
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with null subjectId should fail validation")
    void testInvalidSubjectId() {
        visit.setSubjectId(null);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with null studyId should fail validation")
    void testInvalidStudyId() {
        visit.setStudyId(null);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with null visitCode should fail validation")
    void testInvalidVisitCode() {
        visit.setVisitCode(null);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with invalid status should fail validation")
    void testInvalidStatus() {
        visit.setStatus("invalid_status");
        assertFalse(visit.isValid());
        
        visit.setStatus(null);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with valid statuses should pass validation")
    void testValidStatuses() {
        String[] validStatuses = {"planned", "missed", "cancelled", "draft"};

        for (String status : validStatuses) {
            visit.setStatus(status);
            visit.setActualDate(null); // Clear actual date for non-completed statuses
            assertTrue(visit.isValid(), "Status '" + status + "' should be valid");

            // Test case insensitive
            visit.setStatus(status.toUpperCase());
            assertTrue(visit.isValid(), "Status '" + status.toUpperCase() + "' should be valid");
        }

        // Test completed status separately (requires actual date)
        visit.setStatus("completed");
        visit.setActualDate(LocalDate.now());
        assertTrue(visit.isValid(), "Status 'completed' should be valid with actual date");

        visit.setStatus("COMPLETED");
        assertTrue(visit.isValid(), "Status 'COMPLETED' should be valid with actual date");
    }

    @Test
    @DisplayName("Visit with null plannedDate should fail validation")
    void testInvalidPlannedDate() {
        visit.setPlannedDate(null);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with invalid date logic should fail validation")
    void testInvalidDateLogic() {
        // Actual date too far from planned date
        visit.setActualDate(LocalDate.now().plusYears(2));
        assertFalse(visit.isValid());
        
        // Completed visit without actual date
        visit.setStatus("completed");
        visit.setActualDate(null);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with invalid window configuration should fail validation")
    void testInvalidWindowConfiguration() {
        // Negative window days
        visit.setWindowMinusDays(-1);
        assertFalse(visit.isValid());
        
        visit.setWindowMinusDays(3);
        visit.setWindowPlusDays(-1);
        assertFalse(visit.isValid());
        
        // Excessively large window
        visit.setWindowMinusDays(50);
        visit.setWindowPlusDays(50);
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("Visit with invalid deviations should fail validation")
    void testInvalidDeviations() {
        List<Visit.Deviation> deviations = new ArrayList<>();
        
        // Deviation with missing required fields
        Visit.Deviation invalidDeviation = new Visit.Deviation();
        invalidDeviation.setCode(null); // Missing required field
        invalidDeviation.setDescription("Test deviation");
        invalidDeviation.setSeverity("minor");
        deviations.add(invalidDeviation);
        
        visit.setDeviations(deviations);
        assertFalse(visit.isValid());
        
        // Fix the deviation
        invalidDeviation.setCode("TEST_CODE");
        assertTrue(visit.isValid());
        
        // Invalid severity
        invalidDeviation.setSeverity("invalid_severity");
        assertFalse(visit.isValid());
    }

    @Test
    @DisplayName("isWithinWindow should correctly check date windows")
    void testIsWithinWindow() {
        LocalDate plannedDate = LocalDate.of(2024, 1, 15);
        visit.setPlannedDate(plannedDate);
        visit.setWindowMinusDays(3);
        visit.setWindowPlusDays(3);
        
        // Within window
        assertTrue(visit.isWithinWindow(LocalDate.of(2024, 1, 15))); // Exact date
        assertTrue(visit.isWithinWindow(LocalDate.of(2024, 1, 12))); // 3 days before
        assertTrue(visit.isWithinWindow(LocalDate.of(2024, 1, 18))); // 3 days after
        assertTrue(visit.isWithinWindow(LocalDate.of(2024, 1, 14))); // 1 day before
        assertTrue(visit.isWithinWindow(LocalDate.of(2024, 1, 16))); // 1 day after
        
        // Outside window
        assertFalse(visit.isWithinWindow(LocalDate.of(2024, 1, 11))); // 4 days before
        assertFalse(visit.isWithinWindow(LocalDate.of(2024, 1, 19))); // 4 days after
        
        // Null dates
        assertFalse(visit.isWithinWindow(null));
        visit.setPlannedDate(null);
        assertFalse(visit.isWithinWindow(LocalDate.of(2024, 1, 15)));
    }

    @Test
    @DisplayName("isCompleted should correctly check completion status")
    void testIsCompleted() {
        visit.setStatus("planned");
        assertFalse(visit.isCompleted());
        
        visit.setStatus("completed");
        assertTrue(visit.isCompleted());
        
        visit.setStatus("COMPLETED");
        assertTrue(visit.isCompleted());
        
        visit.setStatus("cancelled");
        assertFalse(visit.isCompleted());
    }

    @Test
    @DisplayName("isLocked should correctly check lock status")
    void testIsLocked() {
        visit.setLocked(null);
        assertFalse(visit.isLocked());
        
        visit.setLocked(false);
        assertFalse(visit.isLocked());
        
        visit.setLocked(true);
        assertTrue(visit.isLocked());
    }

    @Test
    @DisplayName("hasCriticalDeviations should correctly identify critical deviations")
    void testHasCriticalDeviations() {
        // No deviations
        assertFalse(visit.hasCriticalDeviations());
        
        List<Visit.Deviation> deviations = new ArrayList<>();
        
        // Minor deviation only
        Visit.Deviation minorDeviation = new Visit.Deviation();
        minorDeviation.setCode("MINOR_DEV");
        minorDeviation.setDescription("Minor deviation");
        minorDeviation.setSeverity("minor");
        deviations.add(minorDeviation);
        visit.setDeviations(deviations);
        
        assertFalse(visit.hasCriticalDeviations());
        
        // Add major deviation
        Visit.Deviation majorDeviation = new Visit.Deviation();
        majorDeviation.setCode("MAJOR_DEV");
        majorDeviation.setDescription("Major deviation");
        majorDeviation.setSeverity("major");
        deviations.add(majorDeviation);
        
        assertTrue(visit.hasCriticalDeviations());
        
        // Add critical deviation
        Visit.Deviation criticalDeviation = new Visit.Deviation();
        criticalDeviation.setCode("CRITICAL_DEV");
        criticalDeviation.setDescription("Critical deviation");
        criticalDeviation.setSeverity("critical");
        deviations.add(criticalDeviation);
        
        assertTrue(visit.hasCriticalDeviations());
    }

    @Test
    @DisplayName("canBeModified should correctly check modification permissions")
    void testCanBeModified() {
        // Normal planned visit
        assertTrue(visit.canBeModified());
        
        // Locked visit
        visit.setLocked(true);
        assertFalse(visit.canBeModified());
        
        // Completed visit
        visit.setLocked(false);
        visit.setStatus("completed");
        assertFalse(visit.canBeModified());
    }

    @Test
    @DisplayName("canBeCompleted should correctly check completion permissions")
    void testCanBeCompleted() {
        // Normal planned visit
        assertTrue(visit.canBeCompleted());
        
        // Locked visit
        visit.setLocked(true);
        assertFalse(visit.canBeCompleted());
        
        // Already completed
        visit.setLocked(false);
        visit.setStatus("completed");
        assertFalse(visit.canBeCompleted());
        
        // Cancelled visit
        visit.setStatus("cancelled");
        assertFalse(visit.canBeCompleted());
        
        // Missed visit
        visit.setStatus("missed");
        assertFalse(visit.canBeCompleted());
        
        // Visit without planned date
        visit.setStatus("planned");
        visit.setPlannedDate(null);
        assertFalse(visit.canBeCompleted());
    }

    @Test
    @DisplayName("validateForOperation should correctly validate operations")
    void testValidateForOperation() {
        // Complete operation - valid (need actual date)
        visit.setActualDate(LocalDate.now());
        assertDoesNotThrow(() -> visit.validateForOperation("complete"));

        // Complete operation - locked visit
        visit.setLocked(true);
        assertThrows(IllegalStateException.class, () -> visit.validateForOperation("complete"));

        // Complete operation - missing actual date
        visit.setLocked(false);
        visit.setActualDate(null);
        assertThrows(IllegalArgumentException.class, () -> visit.validateForOperation("complete"));
        
        // Lock operation - not completed
        assertThrows(IllegalStateException.class, () -> visit.validateForOperation("lock"));
        
        // Lock operation - completed visit
        visit.setStatus("completed");
        assertDoesNotThrow(() -> visit.validateForOperation("lock"));
        
        // Unknown operation
        assertThrows(IllegalArgumentException.class, () -> visit.validateForOperation("unknown"));
    }

    @Test
    @DisplayName("getWindowStartDate and getWindowEndDate should calculate correctly")
    void testWindowDates() {
        LocalDate plannedDate = LocalDate.of(2024, 1, 15);
        visit.setPlannedDate(plannedDate);
        visit.setWindowMinusDays(5);
        visit.setWindowPlusDays(7);
        
        assertEquals(LocalDate.of(2024, 1, 10), visit.getWindowStartDate());
        assertEquals(LocalDate.of(2024, 1, 22), visit.getWindowEndDate());
        
        // Null planned date
        visit.setPlannedDate(null);
        assertNull(visit.getWindowStartDate());
        assertNull(visit.getWindowEndDate());
    }

    @Test
    @DisplayName("getDaysFromPlanned should calculate correctly")
    void testGetDaysFromPlanned() {
        LocalDate plannedDate = LocalDate.of(2024, 1, 15);
        visit.setPlannedDate(plannedDate);
        
        assertEquals(0L, visit.getDaysFromPlanned(LocalDate.of(2024, 1, 15)));
        assertEquals(-3L, visit.getDaysFromPlanned(LocalDate.of(2024, 1, 12)));
        assertEquals(5L, visit.getDaysFromPlanned(LocalDate.of(2024, 1, 20)));
        
        // Null dates
        assertNull(visit.getDaysFromPlanned(null));
        visit.setPlannedDate(null);
        assertNull(visit.getDaysFromPlanned(LocalDate.of(2024, 1, 15)));
    }

    @Test
    @DisplayName("getDeviationCountBySeverity should count correctly")
    void testGetDeviationCountBySeverity() {
        List<Visit.Deviation> deviations = new ArrayList<>();
        
        // Add deviations with different severities
        for (int i = 0; i < 3; i++) {
            Visit.Deviation minor = new Visit.Deviation();
            minor.setCode("MINOR_" + i);
            minor.setDescription("Minor deviation " + i);
            minor.setSeverity("minor");
            deviations.add(minor);
        }
        
        for (int i = 0; i < 2; i++) {
            Visit.Deviation major = new Visit.Deviation();
            major.setCode("MAJOR_" + i);
            major.setDescription("Major deviation " + i);
            major.setSeverity("major");
            deviations.add(major);
        }
        
        Visit.Deviation critical = new Visit.Deviation();
        critical.setCode("CRITICAL");
        critical.setDescription("Critical deviation");
        critical.setSeverity("critical");
        deviations.add(critical);
        
        visit.setDeviations(deviations);
        
        assertEquals(3L, visit.getDeviationCountBySeverity("minor"));
        assertEquals(2L, visit.getDeviationCountBySeverity("major"));
        assertEquals(1L, visit.getDeviationCountBySeverity("critical"));
        assertEquals(0L, visit.getDeviationCountBySeverity("unknown"));
        assertEquals(0L, visit.getDeviationCountBySeverity(null));
    }
}
