package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.job.version_1.Job;
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
import java.util.UUID;

@Component
public class NormalizeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public NormalizeProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NormalizeProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for normalization")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "NORMALIZING".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // In the prototype we simulate normalization and attach a JSON string summary
        Laureate l1 = new Laureate();
        l1.setLaureateId(UUID.randomUUID().toString());
        l1.setRawFullName("Sample Laureate A");
        l1.setFullName("Sample Laureate A");
        l1.setYear(2020);
        l1.setCategory("Physics");
        l1.setSourceRecord(job.getTechnicalId() + "-rec-0");
        l1.setProvenance(null);

        Laureate l2 = new Laureate();
        l2.setLaureateId(UUID.randomUUID().toString());
        l2.setRawFullName("Sample Laureate B");
        l2.setFullName("Sample Laureate B");
        l2.setYear(2021);
        l2.setCategory("Chemistry");
        l2.setSourceRecord(job.getTechnicalId() + "-rec-1");
        l2.setProvenance(null);

        ObjectNode rs = objectMapper.createObjectNode();
        rs.put("normalizedCount", 2);
        try {
            rs.putPOJO("normalizedSamples", List.of(l1, l2));
            job.setResultSummary(objectMapper.writeValueAsString(rs));
        } catch (Exception e) {
            job.setResultSummary("{}");
        }

        job.setStatus("COMPARING");
        logger.info("Job {} normalization produced {} records", job.getTechnicalId(), 2);

        return job;
    }
}
