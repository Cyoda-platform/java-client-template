package com.java_template.application.criterion.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SubledgerAvailableCriterion.
 */
class SubledgerAvailableCriterionTest {

    private SerializerFactory serializerFactory;
    private SubledgerAvailableCriterion criterion;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of(), List.of((CriterionSerializer) criterionSerializer));
        criterion = new SubledgerAvailableCriterion(serializerFactory);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Criterion(
            new ModelSpec().withName("Accrual").withVersion(1),
            "SubledgerAvailable",
            "NEW",
            "VALIDATE",
            "Accrual Workflow"
        );

        assertTrue(criterion.supports(modelSpec));
    }

    @Test
    void testSupports_withIncorrectName_returnsFalse() {
        var modelSpec = new OperationSpecification.Criterion(
            new ModelSpec().withName("Accrual").withVersion(1),
            "SomeOtherCriterion",
            "NEW",
            "VALIDATE",
            "Accrual Workflow"
        );

        assertFalse(criterion.supports(modelSpec));
    }

    // TODO: Add integration tests that verify:
    // - Sub-ledger service is reachable (returns success)
    // - Sub-ledger service is not reachable (returns failure)
    // - GL accounts are configured for currency (returns success)
    // - GL accounts are not configured for currency (returns failure)
    // - Currency is supported (returns success)
    // - Currency is not supported (returns failure)
}

