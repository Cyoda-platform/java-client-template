package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.util.*;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistLaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistLaureateProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for persisting")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "PERSISTING".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        Object toPersistObj = job.getResultSummary() != null ? job.getResultSummary().get("toPersist") : null;
        if (!(toPersistObj instanceof List)) {
            logger.info("Job {} nothing to persist", job.getTechnicalId());
            job.setStatus("COMPLETED");
            return job;
        }

        List<?> toPersist = (List<?>) toPersistObj;

        // Simulated in-memory persistence store mapped by naturalKey
        Map<String, Laureate> persistedStore = new HashMap<>();
        int created = 0;
        int updated = 0;
        for (Object o : toPersist) {
            if (!(o instanceof Laureate)) continue;
            Laureate p = (Laureate) o;
            String naturalKey = (p.getFullName() + "|" + p.getYear() + "|" + p.getCategory()).toLowerCase();
            Laureate existing = persistedStore.get(naturalKey);
            if (existing == null) {
                // insert
                persistedStore.put(naturalKey, p);
                created++;
                logger.info("Inserted laureate naturalKey={}", naturalKey);
            } else {
                // optimistic lock simulation: compare versions
                if (p.getVersion() > existing.getVersion()) {
                    persistedStore.put(naturalKey, p);
                    updated++;
                    logger.info("Updated laureate naturalKey={} to version={}", naturalKey, p.getVersion());
                } else {
                    logger.info("Skipped persist for laureate naturalKey={} due to same or older version", naturalKey);
                }
            }
        }

        job.setResultSummary(Map.of("created", created, "updated", updated));
        job.setStatus("COMPLETED");
        logger.info("Job {} persist complete created={}, updated={}", job.getTechnicalId(), created, updated);
        return job;
    }
}
