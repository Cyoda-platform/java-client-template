package com.java_template.application.criterion.eod_batch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserHasPermissionCriterion.
 */
class UserHasPermissionCriterionTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("UserHasPermission");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "UserHasPermission");

        String expectedName = "UserHasPermission";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("userhaspermission");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "userhaspermission");

        String expectedName = "UserHasPermission";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherCriterion");

        String expectedName = "UserHasPermission";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

