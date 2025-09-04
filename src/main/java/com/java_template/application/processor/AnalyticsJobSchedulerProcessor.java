package com.java_template.application.processor;

import com.java_template.application.entity.analyticsjob.version_1.AnalyticsJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class AnalyticsJobSchedulerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobSchedulerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalyticsJobSchedulerProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalyticsJob scheduling for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AnalyticsJob.class)
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

    private boolean isValidEntity(AnalyticsJob entity) {
        return entity != null && entity.isValid();
    }

    private AnalyticsJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalyticsJob> context) {
        AnalyticsJob job = context.entity();

        try {
            // Set job scheduling details if not already set
            if (job.getScheduledFor() == null) {
                job.setScheduledFor(getNextWednesdayAt9AM());
            }

            // Generate unique job ID
            int weekNumber = getWeekNumber(job.getScheduledFor());
            int year = job.getScheduledFor().getYear();
            job.setJobId("JOB-" + year + "-W" + String.format("%02d", weekNumber) + "-WED");

            // Set default configuration
            Map<String, Object> config = new HashMap<>();
            config.put("apiUrl", "https://fakerestapi.azurewebsites.net/api/v1/Books");
            config.put("emailRecipients", "analytics-team@company.com");
            config.put("reportType", "WEEKLY_ANALYTICS");
            config.put("maxRetries", 3);
            
            job.setConfigurationData(objectMapper.writeValueAsString(config));

            // Initialize counters
            if (job.getBooksProcessed() == null) {
                job.setBooksProcessed(0);
            }
            if (job.getReportsGenerated() == null) {
                job.setReportsGenerated(0);
            }

            // Schedule next job
            LocalDateTime nextWednesday = job.getScheduledFor().plusWeeks(1);
            AnalyticsJob nextJob = createNextJob(nextWednesday);
            job.setNextJobId(nextJob.getJobId());

            logger.info("Job scheduled: {} for {}", job.getJobId(), 
                job.getScheduledFor().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        } catch (Exception e) {
            logger.error("Failed to schedule job: {}", e.getMessage(), e);
            throw new RuntimeException("Job scheduling failed: " + e.getMessage(), e);
        }

        return job;
    }

    private LocalDateTime getNextWednesdayAt9AM() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextWednesday = now.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
                                        .withHour(9)
                                        .withMinute(0)
                                        .withSecond(0)
                                        .withNano(0);
        
        // If today is Wednesday and it's before 9 AM, schedule for today
        if (now.getDayOfWeek() == DayOfWeek.WEDNESDAY && now.getHour() < 9) {
            nextWednesday = now.withHour(9).withMinute(0).withSecond(0).withNano(0);
        }
        
        return nextWednesday;
    }

    private int getWeekNumber(LocalDateTime dateTime) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return dateTime.get(weekFields.weekOfYear());
    }

    private AnalyticsJob createNextJob(LocalDateTime scheduledFor) {
        try {
            AnalyticsJob nextJob = new AnalyticsJob();
            nextJob.setJobType("WEEKLY_DATA_EXTRACTION");
            nextJob.setScheduledFor(scheduledFor);
            nextJob.setBooksProcessed(0);
            nextJob.setReportsGenerated(0);
            
            // Generate job ID for next job
            int weekNumber = getWeekNumber(scheduledFor);
            int year = scheduledFor.getYear();
            nextJob.setJobId("JOB-" + year + "-W" + String.format("%02d", weekNumber) + "-WED");
            
            // Save the next job entity
            entityService.save(nextJob);
            logger.info("Created next scheduled job: {}", nextJob.getJobId());
            
            return nextJob;
        } catch (Exception e) {
            logger.error("Failed to create next job: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create next job: " + e.getMessage(), e);
        }
    }
}
