package com.java_template.application.processor;

import com.java_template.application.entity.flightoption.version_1.FlightOption;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EnrichFareProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichFareProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichFareProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EnrichFareProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightOption.class)
            .validate(this::isValidEntity, "Invalid entity state for fare enrichment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightOption entity) {
        return entity != null && entity.getStatus() != null && "CREATED".equalsIgnoreCase(entity.getStatus());
    }

    private FlightOption processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightOption> context) {
        FlightOption entity = context.entity();
        try {
            String idForLog = entity.getOptionId() != null ? entity.getOptionId() : "<unknown>";
            if (entity.getFareRules() == null || entity.getFareRules().isEmpty()) {
                logger.debug("Setting status -> ENRICHING for option {}", idForLog);
                entity.setStatus("ENRICHING");

                // For prototype: simulate fare rules enrichment
                String summary = "Fare rules summary: refundable=false, change_fee=200";
                entity.setFareRules(summary);
            }
            return entity;
        } catch (Exception ex) {
            logger.error("Error enriching fare for option {}", entity != null ? entity.getOptionId() : "<null>", ex);
            if (entity != null) {
                entity.setStatus("ERROR");
            }
            return entity;
        }
    }
}
