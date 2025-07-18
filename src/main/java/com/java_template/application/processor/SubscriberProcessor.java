package com.java_template.application.processor;

import com.java_template.application.entity.Subscriber;
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

@Component
public class SubscriberProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public SubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); //always follow this pattern
        logger.info("SubscriberProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(Subscriber.class)
                .validate(Subscriber::isValid, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriberProcessor".equals(modelSpec.operationName()) &&
                "subscriber".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Subscriber processEntityLogic(Subscriber subscriber) {
        logger.info("Processing Subscriber with technicalId: {}", subscriber.getTechnicalId());
        if (subscriber.getEmail() == null || !subscriber.getEmail().matches("^[\\w\\.-]+@[\\w\\.-]+\\.\\w+$")) {
            logger.error("Invalid email format for subscriber technicalId: {}", subscriber.getTechnicalId());
        }
        logger.info("Welcome email sent to: {}", subscriber.getEmail());
        return subscriber;
    }
}
