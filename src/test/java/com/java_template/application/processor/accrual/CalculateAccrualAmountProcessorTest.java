package com.java_template.application.processor.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
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
 * Unit tests for CalculateAccrualAmountProcessor.
 */
@ExtendWith(MockitoExtension.class)
class CalculateAccrualAmountProcessorTest {

    @Mock
    private EntityService entityService;

    private SerializerFactory serializerFactory;
    private CalculateAccrualAmountProcessor processor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of((ProcessorSerializer) processorSerializer), List.of());
        processor = new CalculateAccrualAmountProcessor(serializerFactory, entityService);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "CalculateAccrualAmount",
            "CALCULATED",
            "CALCULATE",
            "Accrual Workflow"
        );

        assertTrue(processor.supports(modelSpec));
    }

    @Test
    void testSupports_withIncorrectName_returnsFalse() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "SomeOtherProcessor",
            "CALCULATED",
            "CALCULATE",
            "Accrual Workflow"
        );

        assertFalse(processor.supports(modelSpec));
    }

    // TODO: Add integration tests for:
    // - Interest calculation with various principal amounts
    // - Interest calculation with various APR values
    // - Interest calculation with various day count fractions
    // - Rounding behavior
    // - Zero or negative amounts
}

