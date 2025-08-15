package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JobFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public JobFetchProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing JobFetchProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for fetching")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "FETCHING".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Simulate fetching from source: respect runId if present in resultSummary
        int page = 0;
        int pageSize = 50; // default page size
        boolean morePages = true;
        List<Map<String, Object>> fetchedRecords = new ArrayList<>();

        while (morePages) {
            List<Map<String, Object>> pageRecords = simulateFetchPage(job, page, pageSize);
            logger.info("Job {} fetched page {} with {} records", job.getTechnicalId(), page, pageRecords.size());

            fetchedRecords.addAll(pageRecords);

            if (pageRecords.size() < pageSize) {
                morePages = false;
            } else {
                page++;
            }
        }

        // Attach a detailed JSON summary in resultSummary string
        ObjectNode rs = objectMapper.createObjectNode();
        rs.put("fetched", fetchedRecords.size());
        rs.put("fetchedAt", Instant.now().toString());
        try {
            job.setResultSummary(objectMapper.writeValueAsString(rs));
        } catch (Exception e) {
            job.setResultSummary("{}");
        }
        job.setStatus("NORMALIZING");
        logger.info("Job {} fetch complete, totalRecords={}", job.getTechnicalId(), fetchedRecords.size());

        return job;
    }

    private List<Map<String, Object>> simulateFetchPage(Job job, int page, int pageSize) {
        List<Map<String, Object>> pageRecords = new ArrayList<>();
        int total = 3; // small fixed total for prototype
        int start = page * pageSize;
        for (int i = start; i < Math.min(start + pageSize, total); i++) {
            pageRecords.add(Map.of(
                "sourceRecordId", job.getTechnicalId() + "-rec-" + i,
                "rawFullName", "Sample Laureate " + i,
                "year", 2000 + i,
                "category", "Category" + (i%3)
            ));
        }
        return pageRecords;
    }
}
