package com.java_template.application.processor;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob job = context.entity();
        if (job == null) {
            logger.warn("ReportJob is null in execution context");
            return null;
        }

        logger.info("NotifySubscribersProcessor started for jobId={} status={}", job.getJobId(), job.getStatus());

        // Mark job as NOTIFYING in-memory (Cyoda will persist)
        job.setStatus("NOTIFYING");

        // Parse notify filters from job (format: "frequency=weekly;area=All")
        Map<String, String> notifyFilters = parseFilters(job.getNotifyFilters());
        String frequencyFilter = notifyFilters.get("frequency");

        // Build search condition: status == ACTIVE and optional frequency
        SearchConditionRequest searchCondition;
        if (frequencyFilter != null && !frequencyFilter.isBlank()) {
            searchCondition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ACTIVE"),
                Condition.of("$.frequency", "EQUALS", frequencyFilter)
            );
        } else {
            searchCondition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ACTIVE")
            );
        }

        List<Subscriber> matchedSubscribers = new ArrayList<>();
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                searchCondition,
                true
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Subscriber sub = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                        if (sub != null && matchesAdditionalFilters(sub, notifyFilters)) {
                            matchedSubscribers.add(sub);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to convert DataPayload to Subscriber: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching subscribers: {}", ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Failed to fetch subscribers by condition: {}", ee.getMessage(), ee);
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching subscribers: {}", ex.getMessage(), ex);
        }

        logger.info("Found {} matching subscribers for jobId={}", matchedSubscribers.size(), job.getJobId());

        // Simulate sending notifications to matched subscribers
        int successCount = 0;
        int failCount = 0;
        for (Subscriber subscriber : matchedSubscribers) {
            try {
                // Simulate send - in real system you'd enqueue an email/send event or call an email service
                logger.info("Sending report '{}' to subscriber {} <{}>", job.getReportLocation(), subscriber.getName(), subscriber.getEmail());
                // Assume send succeeds
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to send report to subscriber {}: {}", subscriber.getSubscriberId(), e.getMessage(), e);
                failCount++;
            }
        }

        logger.info("Notification results for jobId={} : success={}, failed={}", job.getJobId(), successCount, failCount);

        // Update job status based on outcomes. If at least one success mark as COMPLETED, else mark FAILED.
        if (successCount > 0) {
            job.setStatus("COMPLETED");
        } else {
            job.setStatus("FAILED");
        }

        return job;
    }

    private Map<String, String> parseFilters(String filtersStr) {
        if (filtersStr == null || filtersStr.isBlank()) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        String[] parts = filtersStr.split(";");
        for (String p : parts) {
            if (p == null) continue;
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            int idx = trimmed.indexOf('=');
            if (idx > 0 && idx < trimmed.length() - 1) {
                String k = trimmed.substring(0, idx).trim().toLowerCase();
                String v = trimmed.substring(idx + 1).trim();
                map.put(k, v);
            }
        }
        return map;
    }

    private boolean matchesAdditionalFilters(Subscriber subscriber, Map<String, String> notifyFilters) {
        if (notifyFilters == null || notifyFilters.isEmpty()) return true;
        // Frequency already matched by search condition. Only apply other filters here (e.g., area)
        for (Map.Entry<String, String> entry : notifyFilters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) continue;
            if ("frequency".equalsIgnoreCase(key)) continue; // already handled

            // Subscriber stores filters as a serialized string like "area=NW"
            String subscriberFilters = subscriber.getFilters();
            if (subscriberFilters == null || subscriberFilters.isBlank()) {
                // if the job's filter value is 'All' treat as match, otherwise not matching
                if ("all".equalsIgnoreCase(value)) continue;
                return false;
            }
            // simple contains-based match: if subscriber filters contain the value or equal to the value
            if ("all".equalsIgnoreCase(value)) {
                continue;
            }
            if (subscriberFilters.contains(value) || subscriberFilters.equalsIgnoreCase(value)) {
                continue;
            }
            // no match
            return false;
        }
        return true;
    }
}