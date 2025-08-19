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
            .toEntity(Subscriber.class) // try Subscriber first
            .validate(sub -> sub != null, "Invalid entity state")
            .map(this::processSubscriber)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Subscriber processSubscriber(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber sub = context.entity();
        try {
            sub.setSubscriptionStatus("active");
            logger.info("Subscriber {} marked active", sub.getId());
        } catch (Exception ex) {
            logger.error("Error in MarkActiveProcessor (Subscriber): {}", ex.getMessage(), ex);
        }
        return sub;
    }
}
