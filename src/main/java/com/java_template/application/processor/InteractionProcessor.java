package com.java_template.application.processor;

import com.java_template.application.entity.Interaction;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InteractionProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public InteractionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("InteractionProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Interaction for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Interaction.class)
                .validate(Interaction::isValid, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InteractionProcessor".equals(modelSpec.operationName()) &&
                "interaction".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Interaction processEntityLogic(Interaction interaction) {
        logger.info("Processing Interaction with technicalId: {}", interaction.getTechnicalId());

        logger.info("Subscriber {} had interaction {} on CatFactJob {} at {}",
                interaction.getSubscriberId(),
                interaction.getInteractionType(),
                interaction.getCatFactJobId(),
                interaction.getInteractedAt());

        return interaction;
    }
}
