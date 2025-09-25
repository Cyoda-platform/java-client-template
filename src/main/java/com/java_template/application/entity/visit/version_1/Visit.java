package com.java_template.application.entity.visit.version_1;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Visit Entity for Clinical Trial Visit Management
 *
 * Represents a clinical trial visit with scheduling, completion tracking,
 * deviation management, and CRF data collection capabilities.
 */
@Data
public class Visit implements CyodaEntity {
    public static final String ENTITY_NAME = Visit.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String visitId;

    // Study and subject relationships
    private String subjectId;
    private String studyId;

    // Visit identification and scheduling
    private String visitCode; // e.g., "V1", "V2", "SCREENING", "FOLLOWUP"
    private LocalDate plannedDate;
    private LocalDate actualDate;

    // Visit status and management
    private String status; // "planned", "completed", "missed", "cancelled"
    private Boolean locked; // Data lock status for regulatory compliance

    // Visit deviations and protocol violations
    private List<Deviation> deviations;

    // Case Report Form data - flexible JSON structure
    private JsonNode crfData;

    // Visit window and scheduling metadata
    private Integer windowMinusDays; // Days before planned date visit can occur
    private Integer windowPlusDays; // Days after planned date visit can occur
    private List<String> mandatoryProcedures; // Required procedures for this visit

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Visit completion metadata
    private LocalDateTime completedAt;
    private String completedBy;
    private String completionNotes;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return visitId != null && !visitId.trim().isEmpty() &&
               subjectId != null && !subjectId.trim().isEmpty() &&
               studyId != null && !studyId.trim().isEmpty() &&
               visitCode != null && !visitCode.trim().isEmpty() &&
               status != null && !status.trim().isEmpty() &&
               plannedDate != null &&
               isValidStatus(status) &&
               isValidDateLogic() &&
               isValidWindowConfiguration() &&
               areDeviationsValid();
    }

    /**
     * Validates that the status is one of the allowed values
     */
    private boolean isValidStatus(String status) {
        if (status == null) return false;
        String normalizedStatus = status.toLowerCase().trim();
        return normalizedStatus.equals("planned") ||
               normalizedStatus.equals("completed") ||
               normalizedStatus.equals("missed") ||
               normalizedStatus.equals("cancelled") ||
               normalizedStatus.equals("draft");
    }

    /**
     * Validates date logic consistency
     */
    private boolean isValidDateLogic() {
        // Planned date should not be null
        if (plannedDate == null) return false;

        // If actual date is set, validate it's reasonable
        if (actualDate != null) {
            // Actual date should not be more than 1 year before or after planned date
            long daysDifference = Math.abs(plannedDate.toEpochDay() - actualDate.toEpochDay());
            if (daysDifference > 365) {
                return false;
            }
        }

        // If completed, actual date should be set
        if ("completed".equalsIgnoreCase(status) && actualDate == null) {
            return false;
        }

        return true;
    }

    /**
     * Validates visit window configuration
     */
    private boolean isValidWindowConfiguration() {
        // Window days should not be negative
        if (windowMinusDays != null && windowMinusDays < 0) return false;
        if (windowPlusDays != null && windowPlusDays < 0) return false;

        // Window should not be excessively large (business rule)
        if (windowMinusDays != null && windowPlusDays != null) {
            int totalWindow = windowMinusDays + windowPlusDays;
            if (totalWindow > 90) return false; // Max 90 days total window
        }

        return true;
    }

    /**
     * Validates deviation data integrity
     */
    private boolean areDeviationsValid() {
        if (deviations == null || deviations.isEmpty()) return true;

        for (Deviation deviation : deviations) {
            if (!isValidDeviation(deviation)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates individual deviation
     */
    private boolean isValidDeviation(Deviation deviation) {
        if (deviation == null) return false;

        // Required fields
        if (deviation.getCode() == null || deviation.getCode().trim().isEmpty()) return false;
        if (deviation.getDescription() == null || deviation.getDescription().trim().isEmpty()) return false;
        if (deviation.getSeverity() == null || deviation.getSeverity().trim().isEmpty()) return false;

        // Valid severity values
        String severity = deviation.getSeverity().toLowerCase().trim();
        if (!severity.equals("minor") && !severity.equals("major") && !severity.equals("critical")) {
            return false;
        }

        // Valid category values if set
        if (deviation.getCategory() != null) {
            String category = deviation.getCategory().toLowerCase().trim();
            if (!category.equals("timing") && !category.equals("procedure") &&
                !category.equals("data") && !category.equals("protocol")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Nested class for visit deviations and protocol violations
     * Tracks any deviations from the planned visit protocol
     */
    @Data
    public static class Deviation {
        private String deviationId;
        private String code; // Standardized deviation code (e.g., "LATE", "MISSED_PROC", "OUT_OF_WINDOW")
        private String description; // Human-readable description of the deviation
        private String severity; // "minor", "major", "critical"
        private String category; // "timing", "procedure", "data", "protocol"
        private LocalDateTime detectedAt;
        private String detectedBy;
        private String resolution; // How the deviation was resolved
        private String resolutionBy;
        private LocalDateTime resolvedAt;
        private Boolean requiresReporting; // Whether deviation requires regulatory reporting
    }

    /**
     * Helper method to check if visit is within the allowed window
     */
    public boolean isWithinWindow(LocalDate checkDate) {
        if (plannedDate == null || checkDate == null) {
            return false;
        }

        LocalDate earliestDate = (windowMinusDays != null) ?
            plannedDate.minusDays(windowMinusDays) : plannedDate;
        LocalDate latestDate = (windowPlusDays != null) ?
            plannedDate.plusDays(windowPlusDays) : plannedDate;

        return !checkDate.isBefore(earliestDate) && !checkDate.isAfter(latestDate);
    }

    /**
     * Helper method to check if visit is completed
     */
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }

    /**
     * Helper method to check if visit is locked
     */
    public boolean isLocked() {
        return locked != null && locked;
    }

    /**
     * Helper method to check if visit has any major or critical deviations
     */
    public boolean hasCriticalDeviations() {
        if (deviations == null || deviations.isEmpty()) {
            return false;
        }

        return deviations.stream()
            .anyMatch(deviation -> "major".equalsIgnoreCase(deviation.getSeverity()) ||
                                 "critical".equalsIgnoreCase(deviation.getSeverity()));
    }

    /**
     * Helper method to check if visit can be modified
     */
    public boolean canBeModified() {
        return !isLocked() && !"completed".equalsIgnoreCase(status);
    }

    /**
     * Helper method to check if visit can be completed
     */
    public boolean canBeCompleted() {
        return !isLocked() &&
               !"completed".equalsIgnoreCase(status) &&
               !"cancelled".equalsIgnoreCase(status) &&
               !"missed".equalsIgnoreCase(status) &&
               plannedDate != null;
    }

    /**
     * Helper method to check if visit can be cancelled
     */
    public boolean canBeCancelled() {
        return !isLocked() && !"completed".equalsIgnoreCase(status);
    }

    /**
     * Helper method to get visit window start date
     */
    public LocalDate getWindowStartDate() {
        if (plannedDate == null) return null;
        return (windowMinusDays != null) ? plannedDate.minusDays(windowMinusDays) : plannedDate;
    }

    /**
     * Helper method to get visit window end date
     */
    public LocalDate getWindowEndDate() {
        if (plannedDate == null) return null;
        return (windowPlusDays != null) ? plannedDate.plusDays(windowPlusDays) : plannedDate;
    }

    /**
     * Helper method to get days from planned date
     */
    public Long getDaysFromPlanned(LocalDate checkDate) {
        if (plannedDate == null || checkDate == null) return null;
        return checkDate.toEpochDay() - plannedDate.toEpochDay();
    }

    /**
     * Helper method to check if visit requires regulatory reporting
     */
    public boolean requiresRegulatoryReporting() {
        if (deviations == null || deviations.isEmpty()) return false;

        return deviations.stream()
            .anyMatch(deviation -> Boolean.TRUE.equals(deviation.getRequiresReporting()));
    }

    /**
     * Helper method to get count of deviations by severity
     */
    public long getDeviationCountBySeverity(String severity) {
        if (deviations == null || deviations.isEmpty() || severity == null) return 0;

        return deviations.stream()
            .filter(deviation -> severity.equalsIgnoreCase(deviation.getSeverity()))
            .count();
    }

    /**
     * Helper method to validate visit for a specific operation
     */
    public void validateForOperation(String operation) {
        switch (operation.toLowerCase()) {
            case "complete":
                if (!canBeCompleted()) {
                    throw new IllegalStateException("Visit cannot be completed in current state");
                }
                if (actualDate == null) {
                    throw new IllegalArgumentException("Actual date is required for completion");
                }
                break;
            case "cancel":
                if (!canBeCancelled()) {
                    throw new IllegalStateException("Visit cannot be cancelled in current state");
                }
                break;
            case "lock":
                if (!isCompleted()) {
                    throw new IllegalStateException("Only completed visits can be locked");
                }
                break;
            case "modify":
                if (!canBeModified()) {
                    throw new IllegalStateException("Visit cannot be modified in current state");
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }
}
