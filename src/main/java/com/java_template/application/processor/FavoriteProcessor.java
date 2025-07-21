package com.java_template.application.processor;

import com.java_template.application.entity.Favorite;
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
public class FavoriteProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public FavoriteProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("FavoriteProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Favorite for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Favorite.class)
            .validate(Favorite::isValid, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FavoriteProcessor".equals(modelSpec.operationName()) &&
               "favorite".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Favorite processEntityLogic(Favorite favorite) {
        logger.info("Processing Favorite with technicalId: {}", favorite.getTechnicalId());
        if (favorite.getUserId() == null || favorite.getUserId().isBlank()) {
            logger.error("Favorite userId is invalid");
            return favorite;
        }
        if (favorite.getPetId() == null || favorite.getPetId().isBlank()) {
            logger.error("Favorite petId is invalid");
            return favorite;
        }
        logger.info("User {} favorited pet {}", favorite.getUserId(), favorite.getPetId());
        return favorite;
    }
}
