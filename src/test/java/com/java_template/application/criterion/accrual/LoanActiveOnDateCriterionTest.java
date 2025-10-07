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
 * Unit tests for LoanActiveOnDateCriterion.
 */
@ExtendWith(MockitoExtension.class)
class LoanActiveOnDateCriterionTest {

    @Mock
    private EntityService entityService;

    private SerializerFactory serializerFactory;
    private LoanActiveOnDateCriterion criterion;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of(), List.of((CriterionSerializer) criterionSerializer));
        criterion = new LoanActiveOnDateCriterion(serializerFactory, entityService);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Criterion(
            new ModelSpec().withName("Accrual").withVersion(1),
            "LoanActiveOnDate",
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
    // - Loan exists and is active on the asOfDate
    // - Loan does not exist (returns failure)
    // - AsOfDate is before funding date (returns failure)
    // - AsOfDate is on or after maturity date (returns failure)
    // - Loan is in NON_ACCRUAL status (returns failure)
    // - Loan has been charged off (returns failure)
}

