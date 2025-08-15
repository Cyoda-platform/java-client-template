package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final ObjectMapper objectMapper;

    public DedupAndVersionProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
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
        // read normalizedSamples from job.resultSummary JSON string
        List<Laureate> toPersist = new ArrayList<>();

        try {
            if (job.getResultSummary() == null) {
                logger.warn("Job {} has no resultSummary", job.getTechnicalId());
                job.setStatus("PERSISTING");
                return job;
            }
            JsonNode rs = objectMapper.readTree(job.getResultSummary());
            JsonNode samples = rs.get("normalizedSamples");
            if (samples != null && samples.isArray()) {
                Map<String, Laureate> simulatedDb = new HashMap<>();
                for (JsonNode s : samples) {
                    Laureate n = objectMapper.treeToValue(s, Laureate.class);
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
            }
        } catch (Exception e) {
            logger.warn("Failed to parse normalized samples for job {}: {}", job.getTechnicalId(), e.getMessage());
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("toPersistCount", toPersist.size());
        out.putPOJO("toPersist", toPersist);
        try {
            job.setResultSummary(objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            job.setResultSummary("{}");
        }
        job.setStatus("PERSISTING");
        logger.info("Job {} dedup/version complete, toPersistCount={}", job.getTechnicalId(), toPersist.size());

        return job;
    }

    private String computeNaturalKey(Laureate l) {
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
