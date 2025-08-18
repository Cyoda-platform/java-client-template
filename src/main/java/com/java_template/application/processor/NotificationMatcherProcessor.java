package com.java_template.application.processor;

import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class NotificationMatcherProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationMatcherProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotificationMatcherProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotificationMatcher for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ChangeEvent.class)
            .validate(this::isValidEntity, "Invalid change event for matching")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ChangeEvent evt) {
        return evt != null && evt.getPayload() != null;
    }

    private ChangeEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ChangeEvent> context) {
        ChangeEvent evt = context.entity();
        try {
            // In real implementation we'd query subscriber repository with filters. Here we simulate.
            List<Subscriber> matched = new ArrayList<>();
            Map payload = evt.getPayload();
            Map laureate = (Map) payload.get("laureate");
            String category = laureate != null ? (String) laureate.get("category") : null;
            String year = laureate != null ? (String) laureate.get("year") : null;

            // Very small simulated subscriber store
            Subscriber s = new Subscriber();
            s.setTechnicalId("sub-sim-1");
            s.setContact("https://webhook.example.com/notify");
            s.setContactType(Subscriber.ContactType.WEBHOOK);
            s.setStatus(Subscriber.Status.ACTIVE);
            s.setChannels(java.util.Arrays.asList(Subscriber.Channel.WEBHOOK));
            s.setFilters(java.util.Map.of("category", java.util.Arrays.asList("physics")));

            if (category != null && category.equalsIgnoreCase("physics")) {
                matched.add(s);
            }

            evt.setDeliveryRecords(new ArrayList<>());
            for (Subscriber sub : matched) {
                // Attach minimal delivery record placeholder
                evt.getDeliveryRecords().add(java.util.Map.of("subscriberTechnicalId", sub.getTechnicalId()));
            }
            logger.info("Matched {} subscribers for event {}", matched.size(), evt.getEventId());
        } catch (Exception e) {
            logger.warn("Error matching subscribers for event {}: {}", evt.getEventId(), e.getMessage());
        }
        return evt;
    }
}
