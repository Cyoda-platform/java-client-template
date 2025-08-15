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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScheduleNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScheduleNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScheduleNotificationProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for scheduling notifications")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate l) {
        return l != null && l.getLaureateId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate l = context.entity();

        // For prototype, read matchedSubscribers from provenance and create simple notification items
        Map<String, Object> prov = l.getProvenance();
        Object matches = prov != null ? prov.get("matchedSubscribers") : null;

        List<Map<String, Object>> notifications = new ArrayList<>();
        if (matches instanceof List) {
            for (Object sid : (List<?>) matches) {
                notifications.add(Map.of(
                    "notificationId", l.getLaureateId() + "-not-" + sid,
                    "subscriberId", sid,
                    "laureateId", l.getLaureateId(),
                    "deliveryPreference", "IMMEDIATE",
                    "changeType", l.getLifecycleStatus()
                ));
            }
        }

        l.setProvenance(addNotificationsToProvenance(l.getProvenance(), notifications));
        logger.info("Scheduled {} notifications for laureate {}", notifications.size(), l.getLaureateId());
        return l;
    }

    private Map<String, Object> addNotificationsToProvenance(Map<String, Object> prov, List<Map<String, Object>> notifications) {
        if (prov == null) prov = Map.of();
        prov.put("notifications", notifications);
        return prov;
    }
}
