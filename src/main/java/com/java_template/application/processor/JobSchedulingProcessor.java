package com.java_template.application.processor;

import com.java_template.application.entity.dataextractionjob.version_1.DataExtractionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

@Component
public class JobSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public JobSchedulingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataExtractionJob scheduling for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataExtractionJob.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataExtractionJob entity) {
        return entity != null && entity.isValid();
    }

    private DataExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataExtractionJob> context) {
        DataExtractionJob entity = context.entity();

        logger.info("Scheduling data extraction job: {}", entity.getJobName());

        // Set job parameters if not already set
        if (entity.getJobName() == null || entity.getJobName().trim().isEmpty()) {
            entity.setJobName("Weekly Pet Store Data Extraction");
        }

        // Set scheduled time if not provided (every Monday at 9:00 AM)
        if (entity.getScheduledTime() == null) {
            LocalDateTime nextMonday = LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .with(LocalTime.of(9, 0));
            entity.setScheduledTime(nextMonday);
        }

        // Set extraction type if not provided
        if (entity.getExtractionType() == null || entity.getExtractionType().trim().isEmpty()) {
            entity.setExtractionType("PRODUCTS");
        }

        // Set API endpoint if not provided
        if (entity.getApiEndpoint() == null || entity.getApiEndpoint().trim().isEmpty()) {
            entity.setApiEndpoint("https://petstore.swagger.io/v2");
        }

        // Initialize counters
        if (entity.getRecordsExtracted() == null) {
            entity.setRecordsExtracted(0);
        }
        if (entity.getRecordsProcessed() == null) {
            entity.setRecordsProcessed(0);
        }
        if (entity.getRecordsFailed() == null) {
            entity.setRecordsFailed(0);
        }

        // Set next scheduled run (7 days after current scheduled time)
        if (entity.getNextScheduledRun() == null) {
            entity.setNextScheduledRun(entity.getScheduledTime().plusDays(7));
        }

        logger.info("Data extraction job scheduled: {} for {}", 
                   entity.getJobName(), entity.getScheduledTime());
        return entity;
    }
}
