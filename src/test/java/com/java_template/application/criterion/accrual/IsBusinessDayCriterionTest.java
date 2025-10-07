package com.java_template.application.criterion.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for IsBusinessDayCriterion.
 */
class IsBusinessDayCriterionTest {

    private SerializerFactory serializerFactory;
    private IsBusinessDayCriterion criterion;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Create real serializer factory for testing
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of(), List.of((CriterionSerializer) criterionSerializer));
        criterion = new IsBusinessDayCriterion(serializerFactory);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new com.java_template.common.workflow.OperationSpecification.Criterion(
            new ModelSpec().withName("Accrual").withVersion(1),
            "IsBusinessDay",
            "NEW",
            "VALIDATE",
            "Accrual Workflow"
        );

        assertTrue(criterion.supports(modelSpec));
    }

    @Test
    void testSupports_withIncorrectName_returnsFalse() {
        var modelSpec = new com.java_template.common.workflow.OperationSpecification.Criterion(
            new ModelSpec().withName("Accrual").withVersion(1),
            "SomeOtherCriterion",
            "NEW",
            "VALIDATE",
            "Accrual Workflow"
        );

        assertFalse(criterion.supports(modelSpec));
    }

    // TODO: Add integration tests that verify:
    // - Valid business day (Monday-Friday, not a holiday) returns success
    // - Saturday returns failure
    // - Sunday returns failure
    // - Holiday returns failure
    // - Null asOfDate returns failure
    // Note: Integration tests require proper setup of EntityWithMetadata and request context
}

