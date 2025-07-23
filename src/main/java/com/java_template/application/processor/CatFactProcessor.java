package com.java_template.application.processor;

import com.java_template.application.entity.CatFact;
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
public class CatFactProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public CatFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("CatFactProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(CatFact::isValid, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CatFactProcessor".equals(modelSpec.operationName()) &&
               "catFact".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private CatFact processEntityLogic(CatFact catFact) {
        logger.info("Processing CatFact with ID: {}", catFact.getId());

        if (catFact.getFact() != null && catFact.getFact().length() < 10) {
            logger.error("CatFact fact is too short");
            catFact.setStatus("ARCHIVED");
        } else {
            logger.info("CatFact fact is valid");
        }
        return catFact;
    }
}
