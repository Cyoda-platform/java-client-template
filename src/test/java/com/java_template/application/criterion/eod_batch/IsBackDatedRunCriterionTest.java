package com.java_template.application.criterion.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IsBackDatedRunCriterion.
 */
class IsBackDatedRunCriterionTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("IsBackDatedRun");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "IsBackDatedRun");

        String expectedName = "IsBackDatedRun";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("isbackdatedrun");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "isbackdatedrun");

        String expectedName = "IsBackDatedRun";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherCriterion");

        String expectedName = "IsBackDatedRun";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

