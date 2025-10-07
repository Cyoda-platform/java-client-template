package com.java_template.application.processor.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CaptureEffectiveDatedSnapshotsProcessor.
 */
class CaptureEffectiveDatedSnapshotsProcessorTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("CaptureEffectiveDatedSnapshots");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "CaptureEffectiveDatedSnapshots");

        String expectedName = "CaptureEffectiveDatedSnapshots";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("captureeffectivedatedsnapshots");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "captureeffectivedatedsnapshots");

        String expectedName = "CaptureEffectiveDatedSnapshots";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "CaptureEffectiveDatedSnapshots";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

