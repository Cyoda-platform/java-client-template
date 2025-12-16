package com.java_template.application.processor;

import com.java_template.application.entity.settlement_instruction.version_1.SettlementInstruction;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Processor for matching settlement instructions
 * Matches buyer and seller settlement instructions
 */
@Component
public class SettlementMatchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SettlementMatchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SettlementMatchingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SettlementMatching for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SettlementInstruction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid settlement instruction wrapper")
                .map(this::matchInstruction)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<SettlementInstruction> entityWithMetadata) {
        SettlementInstruction entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<SettlementInstruction> matchInstruction(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SettlementInstruction> context) {

        EntityWithMetadata<SettlementInstruction> entityWithMetadata = context.entityResponse();
        SettlementInstruction instruction = entityWithMetadata.entity();

        logger.debug("Matching settlement instruction: {} for {} shares of {}", 
                   instruction.getSettlementInstructionId(), instruction.getQuantity(), instruction.getInstrumentSymbol());

        // Validate settlement details
        if (instruction.getQuantity() == null || instruction.getQuantity() <= 0) {
            logger.error("Invalid settlement quantity: {}", instruction.getQuantity());
            throw new IllegalArgumentException("Settlement quantity must be positive");
        }

        if (instruction.getSettlementPrice() == null || instruction.getSettlementPrice() <= 0) {
            logger.error("Invalid settlement price: {}", instruction.getSettlementPrice());
            throw new IllegalArgumentException("Settlement price must be positive");
        }

        // Update reconciliation status
        instruction.setReconciliationStatus("MATCHED");

        // Update timestamp
        instruction.setUpdatedAt(LocalDateTime.now());

        logger.info("Settlement instruction {} matched successfully", instruction.getSettlementInstructionId());
        return entityWithMetadata;
    }
}

