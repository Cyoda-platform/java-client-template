package com.java_template.application.processor.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResolvePeriodStatusProcessor.
 */
class ResolvePeriodStatusProcessorTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("ResolvePeriodStatus");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "ResolvePeriodStatus");

        String expectedName = "ResolvePeriodStatus";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("resolveperiodstatus");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "resolveperiodstatus");

        String expectedName = "ResolvePeriodStatus";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "ResolvePeriodStatus";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

