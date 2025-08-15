package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScheduleNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public ScheduleNotificationProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
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

        // For prototype, read matchedSubscribers from sourceRecord JSON and create simple notification items
        Map<String, Object> prov = new HashMap<>();
        try {
            if (l.getSourceRecord() != null && !l.getSourceRecord().isBlank()) {
                prov = objectMapper.readValue(l.getSourceRecord(), Map.class);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse sourceRecord provenance for laureate {}: {}", l.getLaureateId(), e.getMessage());
            prov = new HashMap<>();
        }

        Object matches = prov.get("matchedSubscribers");

        List<Map<String, Object>> notifications = new ArrayList<>();
        if (matches instanceof List) {
            for (Object sid : (List<?>) matches) {
                String subscriberId = sid == null ? null : sid.toString();
                notifications.add(Map.of(
                    "notificationId", l.getLaureateId() + "-not-" + (subscriberId == null ? "unknown" : subscriberId),
                    "subscriberId", subscriberId,
                    "laureateId", l.getLaureateId(),
                    "deliveryPreference", "IMMEDIATE",
                    "changeType", l.getLifecycleStatus()
                ));
            }
        }

        Map<String, Object> newProv = new HashMap<>(prov);
        newProv.put("notifications", notifications);
        l.setProvenance(newProv);
        logger.info("Scheduled {} notifications for laureate {}", notifications.size(), l.getLaureateId());
        return l;
    }
}
