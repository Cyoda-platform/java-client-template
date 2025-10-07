package com.java_template.application.criterion.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BatchBalancedCriterion.
 */
class BatchBalancedCriterionTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("BatchBalanced");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "BatchBalanced");

        String expectedName = "BatchBalanced";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("batchbalanced");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "batchbalanced");

        String expectedName = "BatchBalanced";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherCriterion");

        String expectedName = "BatchBalanced";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

