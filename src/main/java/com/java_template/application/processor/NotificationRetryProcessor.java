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
public class NotificationRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotificationRetryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotificationRetryProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for notification retry")
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
        int retried = 0;
        for (Object o : notifications) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> n = (Map<?, ?>) o;
            // prototype: mark non-IMMEDIATE as retried and then delivered
            if (!"IMMEDIATE".equals(n.get("deliveryPreference"))) {
                retried++;
                logger.info("Retried notification {} for subscriber {}", n.get("notificationId"), n.get("subscriberId"));
            }
        }

        l.setProvenance(addRetrySummary(l.getProvenance(), retried));
        logger.info("Notification retry summary for laureate {} retried={}", l.getLaureateId(), retried);
        return l;
    }

    private Map<String, Object> addRetrySummary(Map<String, Object> prov, int retried) {
        prov.put("retried", retried);
        return prov;
    }
}
