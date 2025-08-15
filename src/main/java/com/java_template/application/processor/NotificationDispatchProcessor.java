package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.util.List;
import java.util.Map;

@Component
public class NotificationDispatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotificationDispatchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotificationDispatchProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for dispatch")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate l) {
        return l != null && l.getProvenance() != null && l.getProvenance().get("notifications") != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate l = context.entity();
        Object notifs = l.getProvenance().get("notifications");
        if (!(notifs instanceof List)) return l;

        List<?> notifications = (List<?>) notifs;
        int delivered = 0;
        int failed = 0;

        for (Object o : notifications) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> n = (Map<?, ?>) o;
            // In real system deliver via email/webhook/SMS. Here simulate success for IMMEDIATE.
            if ("IMMEDIATE".equals(n.get("deliveryPreference"))) {
                delivered++;
                logger.info("Delivered notification {} to subscriber {}", n.get("notificationId"), n.get("subscriberId"));
            } else {
                failed++;
                logger.warn("Failed to deliver notification {} to subscriber {} (unsupported preference)", n.get("notificationId"), n.get("subscriberId"));
            }
        }

        l.setProvenance(addDeliverySummary(l.getProvenance(), delivered, failed));
        logger.info("Notification dispatch summary for laureate {} delivered={}, failed={}", l.getLaureateId(), delivered, failed);
        return l;
    }

    private Map<String, Object> addDeliverySummary(Map<String, Object> prov, int delivered, int failed) {
        prov.put("delivered", delivered);
        prov.put("failed", failed);
        return prov;
    }
}
