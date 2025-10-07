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
 * Unit tests for UpdateLoanAccruedInterestProcessor.
 */
@ExtendWith(MockitoExtension.class)
class UpdateLoanAccruedInterestProcessorTest {

    @Mock
    private EntityService entityService;

    private SerializerFactory serializerFactory;
    private UpdateLoanAccruedInterestProcessor processor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of((ProcessorSerializer) processorSerializer), List.of());
        processor = new UpdateLoanAccruedInterestProcessor(serializerFactory, entityService);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "UpdateLoanAccruedInterest",
            "POSTED",
            "WRITE_JOURNALS",
            "Accrual Workflow"
        );

        assertTrue(processor.supports(modelSpec));
    }

    @Test
    void testSupports_withIncorrectName_returnsFalse() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "SomeOtherProcessor",
            "POSTED",
            "WRITE_JOURNALS",
            "Accrual Workflow"
        );

        assertFalse(processor.supports(modelSpec));
    }

    // TODO: Add integration tests for:
    // - Loan balance is updated correctly with ORIGINAL entries
    // - Loan balance is updated correctly with REVERSAL entries
    // - Loan balance is updated correctly with REPLACEMENT entries
    // - Net delta calculation is correct
    // - Loan entity is retrieved and updated via EntityService
}

