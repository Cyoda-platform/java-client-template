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
public class DedupAndVersionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DedupAndVersionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DedupAndVersionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DedupAndVersionProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for dedup/version")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "COMPARING".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        Object normalizedObj = job.getResultSummary() != null ? job.getResultSummary().get("normalizedSamples") : null;
        if (!(normalizedObj instanceof List)) {
            logger.warn("Job {} has no normalized samples to compare", job.getTechnicalId());
            job.setStatus("PERSISTING");
            return job;
        }

        List<?> normalized = (List<?>) normalizedObj;
        List<Laureate> toPersist = new ArrayList<>();

        // For prototype we use a simple in-memory map to simulate existing DB by naturalKey
        Map<String, Laureate> simulatedDb = new HashMap<>();

        for (Object o : normalized) {
            if (!(o instanceof Laureate)) continue;
            Laureate n = (Laureate) o;
            String naturalKey = computeNaturalKey(n);
            Laureate existing = simulatedDb.get(naturalKey);
            if (existing == null) {
                n.setLifecycleStatus("NEW");
                n.setVersion(1);
                toPersist.add(n);
                simulatedDb.put(naturalKey, n);
                logger.info("Detected NEW laureate naturalKey={}", naturalKey);
            } else {
                if (hasSignificantChange(existing, n)) {
                    n.setLifecycleStatus("UPDATED");
                    n.setVersion(existing.getVersion() + 1);
                    toPersist.add(n);
                    simulatedDb.put(naturalKey, n);
                    logger.info("Detected UPDATED laureate naturalKey={}", naturalKey);
                } else {
                    n.setLifecycleStatus("UNCHANGED");
                    n.setVersion(existing.getVersion());
                    logger.info("Detected UNCHANGED laureate naturalKey={}", naturalKey);
                }
            }
        }

        job.setResultSummary(Map.of("toPersistCount", toPersist.size(), "toPersist", toPersist));
        job.setStatus("PERSISTING");
        logger.info("Job {} dedup/version complete, toPersistCount={}", job.getTechnicalId(), toPersist.size());

        return job;
    }

    private String computeNaturalKey(Laureate l) {
        // Simple natural key: normalized fullName + year + category
        return (l.getFullName() + "|" + l.getYear() + "|" + l.getCategory()).toLowerCase();
    }

    private boolean hasSignificantChange(Laureate existing, Laureate candidate) {
        if (existing == null || candidate == null) return true;
        if (!Objects.equals(existing.getFullName(), candidate.getFullName())) return true;
        if (!Objects.equals(existing.getYear(), candidate.getYear())) return true;
        if (!Objects.equals(existing.getCategory(), candidate.getCategory())) return true;
        return false;
    }
}
