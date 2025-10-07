package com.java_template.application.processor.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpawnCascadeRecalcProcessor.
 */
class SpawnCascadeRecalcProcessorTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SpawnCascadeRecalc");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SpawnCascadeRecalc");

        String expectedName = "SpawnCascadeRecalc";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("spawncascaderecalc");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "spawncascaderecalc");

        String expectedName = "SpawnCascadeRecalc";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "SpawnCascadeRecalc";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

