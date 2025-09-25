package com.java_template.application.processor;

import com.java_template.application.entity.participant.version_1.Participant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class ParticipantRandomizationProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantRandomizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ParticipantRandomizationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Participant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid participant entity wrapper")
                .map(this::processEntity)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Participant> entityWithMetadata) {
        return entityWithMetadata \!= null && entityWithMetadata.entity() \!= null && entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Participant> processEntity(EntityWithMetadata<Participant> entityWithMetadata) {
        Participant participant = entityWithMetadata.entity();
        logger.info("Processing {} for participant: {}", className, participant.getParticipantId());
        participant.setUpdatedAt(LocalDateTime.now());
        return entityWithMetadata;
    }
}
