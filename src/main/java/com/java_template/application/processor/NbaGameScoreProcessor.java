package com.java_template.application.processor;

import com.java_template.application.entity.NbaGameScore;
import com.java_template.common.serializer.ErrorInfo;
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
import java.util.Objects;

@Component
public class NbaGameScoreProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public NbaGameScoreProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("NbaGameScoreProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NbaGameScore for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(NbaGameScore.class)
                .validate(this::isValidEntity, "Invalid NbaGameScore entity")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaGameScoreProcessor".equals(modelSpec.operationName()) &&
                "nbaGameScore".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(NbaGameScore entity) {
        return entity != null && entity.isValid();
    }

    private NbaGameScore processEntityLogic(NbaGameScore gameScore) {
        logger.info("Processing NbaGameScore with technicalId: {}", gameScore.getTechnicalId());

        // Validate completeness of score data - based on isValid method in POJO
        if (!gameScore.isValid()) {
            logger.warn("NbaGameScore entity is not valid: {}", gameScore);
            return gameScore;
        }

        // Update status to PROCESSED
        gameScore.setStatus("PROCESSED");

        // Trigger notifications for subscribers interested in teams
        // NOTE: Actual notification triggering logic should be implemented elsewhere,
        // here we just prepare the entity as per requirements.

        return gameScore;
    }
}
