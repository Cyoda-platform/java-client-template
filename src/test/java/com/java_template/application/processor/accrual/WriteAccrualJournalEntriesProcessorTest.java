package com.java_template.application.processor.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for WriteAccrualJournalEntriesProcessor.
 */
class WriteAccrualJournalEntriesProcessorTest {

    private SerializerFactory serializerFactory;
    private WriteAccrualJournalEntriesProcessor processor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of((ProcessorSerializer) processorSerializer), List.of());
        processor = new WriteAccrualJournalEntriesProcessor(serializerFactory);
    }

    @Test
    void testSupports_withCorrectName_returnsTrue() {
        var modelSpec = new OperationSpecification.Processor(
            new ModelSpec().withName("Accrual").withVersion(1),
            "WriteAccrualJournalEntries",
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
    // - DR/CR entries are created correctly
    // - Entry IDs are unique
    // - Amounts are balanced (DR total = CR total)
    // - Entry kind is ORIGINAL
    // - Account types are correct (INTEREST_RECEIVABLE, INTEREST_INCOME)
}

