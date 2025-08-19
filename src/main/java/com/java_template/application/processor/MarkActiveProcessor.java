package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class MarkActiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkActiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkActiveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarkActive for request: {}", request.getId());

        // This processor may be used for both Subscriber and CatFact depending on workflow transition
        return serializer.withRequest(request)
            .toEntity(Object.class) // use Object to accept either type, then branch
            .validate(obj -> obj != null, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Object processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Object> context) {
        Object entity = context.entity();
        try {
            if (entity instanceof Subscriber) {
                Subscriber sub = (Subscriber) entity;
                sub.setSubscriptionStatus("active");
                logger.info("Subscriber {} marked active", sub.getId());
                return sub;
            }

            if (entity instanceof CatFact) {
                CatFact fact = (CatFact) entity;
                fact.setStatus("active");
                logger.info("CatFact {} marked active", fact.getId());
                return fact;
            }

            logger.warn("MarkActiveProcessor received unsupported entity type: {}", entity.getClass());
        } catch (Exception ex) {
            logger.error("Error in MarkActiveProcessor: {}", ex.getMessage(), ex);
        }
        return entity;
    }
}
