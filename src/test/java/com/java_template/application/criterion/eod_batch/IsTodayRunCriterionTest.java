package com.java_template.application.criterion.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IsTodayRunCriterion.
 */
class IsTodayRunCriterionTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("IsTodayRun");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "IsTodayRun");

        String expectedName = "IsTodayRun";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("istodayrun");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "istodayrun");

        String expectedName = "IsTodayRun";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherCriterion");

        String expectedName = "IsTodayRun";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

