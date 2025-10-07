package com.java_template.application.criterion.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for RequiresRebookCriterion.
 */
@ExtendWith(MockitoExtension.class)
class RequiresRebookCriterionTest {

    @Mock
    private EntityService entityService;

    private SerializerFactory serializerFactory;
    private RequiresRebookCriterion criterion;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of(), List.of((CriterionSerializer) criterionSerializer));
        criterion = new RequiresRebookCriterion(serializerFactory, entityService);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Criterion(
            new ModelSpec().withName("Accrual").withVersion(1),
            "RequiresRebook",
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
    // - Accrual is in POSTED state and requires rebook (returns success)
    // - Accrual is not in POSTED state (returns failure)
    // - Principal has changed materially (returns success)
    // - APR has changed materially (returns success)
    // - Day count convention has changed (returns success)
    // - No material changes detected (returns failure)
    // - Delta is below materiality threshold (returns failure)
}

