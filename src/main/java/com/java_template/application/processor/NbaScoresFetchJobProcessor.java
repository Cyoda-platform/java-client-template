package com.java_template.application.processor;

import com.java_template.application.entity.NbaScoresFetchJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NbaScoresFetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public NbaScoresFetchJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("NbaScoresFetchJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NbaScoresFetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(NbaScoresFetchJob.class)
                .validate(this::isValidScheduledDate)
                .map(this::processNbaScoresFetchJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaScoresFetchJobProcessor".equals(modelSpec.operationName()) &&
                "nbascoresfetchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidScheduledDate(NbaScoresFetchJob job) {
        LocalDate scheduledDate = job.getScheduledDate();
        if (scheduledDate == null) {
            logger.error("Scheduled date is null");
            return false;
        }
        if (scheduledDate.isAfter(LocalDate.now())) {
            logger.error("Scheduled date is in the future: {}", scheduledDate);
            return false;
        }
        return true;
    }

    private NbaScoresFetchJob processNbaScoresFetchJobLogic(NbaScoresFetchJob job) {
        // Simulate external API call and processing
        try {
            // Fetch external NBA API data for scheduledDate (simulate)
            logger.info("Fetching NBA scores for date: {}", job.getScheduledDate());

            // Simulated fetched games
            // In real code, this would be an API call and data parsing
            // For each fetched game, create an immutable NbaGame entity with status REPORTED
            // Add code here if needed to interact with EntityService for adding NbaGame entities

            // Update job status to COMPLETED
            job.setStatus("COMPLETED");
            job.setSummary("NBA scores fetched and processed successfully.");
        } catch (Exception e) {
            logger.error("Error fetching NBA scores: {}", e.getMessage());
            job.setStatus("FAILED");
            job.setSummary("Failed to fetch NBA scores: " + e.getMessage());
        }
        return job;
    }
}
