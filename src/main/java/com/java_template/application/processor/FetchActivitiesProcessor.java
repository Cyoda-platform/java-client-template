package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.activity.version_1.Activity;
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

@Component
public class FetchActivitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchActivitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchActivitiesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchActivities for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.getRunDate() != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        try {
            // Simulate fetching pages from external source. In real implementation this would call HTTP client.
            List<Activity> fetched = new ArrayList<>();

            // For demonstration, generate a few fake activities based on runDate
            for (int i = 0; i < 5; i++) {
                Activity a = new Activity();
                a.setActivityId(job.getJobId() + "-act-" + i);
                a.setUserId("user-" + (i % 3 + 1));
                a.setTimestamp(Instant.now().toString());
                a.setType(i % 2 == 0 ? "login" : "purchase");
                a.setSource(job.getSource());
                a.setProcessed(false);
                a.setValid(null);
                a.setPersistedAt(Instant.now().toString());
                fetched.add(a);
            }

            // attach fetched activities into job summary for downstream processors to pick
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.valueToTree(fetched);
            job.setSummary(node);
            logger.info("Fetched {} activities for job {}", fetched.size(), job.getJobId());
        } catch (Exception ex) {
            logger.error("Error fetching activities", ex);
            job.setFailureReason("fetch error: " + ex.getMessage());
        }

        return job;
    }
}
