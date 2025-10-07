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
 * Unit tests for ReversePriorJournalsProcessor.
 */
@ExtendWith(MockitoExtension.class)
class ReversePriorJournalsProcessorTest {

    @Mock
    private EntityService entityService;

    private SerializerFactory serializerFactory;
    private ReversePriorJournalsProcessor processor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of((ProcessorSerializer) processorSerializer), List.of());
        processor = new ReversePriorJournalsProcessor(serializerFactory, entityService);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "ReversePriorJournals",
            "SUPERSEDED",
            "SUPERSEDE_AND_REBOOK",
            "Accrual Workflow"
        );

        assertTrue(processor.supports(modelSpec));
    }

    @Test
    void testSupports_withIncorrectName_returnsFalse() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "SomeOtherProcessor",
            "SUPERSEDED",
            "SUPERSEDE_AND_REBOOK",
            "Accrual Workflow"
        );

        assertFalse(processor.supports(modelSpec));
    }

    // TODO: Add integration tests for:
    // - REVERSAL entries are created for each ORIGINAL entry
    // - REVERSAL entries have opposite direction
    // - adjustsEntryId links to original entry
    // - Entry kind is REVERSAL
    // - Prior accrual is retrieved via EntityService
}

