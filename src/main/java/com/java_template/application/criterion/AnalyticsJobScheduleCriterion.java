package com.java_template.application.criterion;

import com.java_template.application.entity.analyticsjob.version_1.AnalyticsJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AnalyticsJobScheduleCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AnalyticsJobScheduleCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Validating AnalyticsJob schedule readiness for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(AnalyticsJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AnalyticsJob> context) {
        AnalyticsJob job = context.entity();
        
        // Validate job ID
        if (job.getJobId() == null || job.getJobId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Job ID is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate scheduled time
        if (job.getScheduledFor() == null) {
            return EvaluationOutcome.fail("Scheduled time is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (job.getScheduledFor().isAfter(LocalDateTime.now())) {
            return EvaluationOutcome.fail("Job is not yet ready to execute (scheduled for future)", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate configuration data
        if (job.getConfigurationData() == null || job.getConfigurationData().length() < 10) {
            return EvaluationOutcome.fail("Configuration data is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!isValidJson(job.getConfigurationData())) {
            return EvaluationOutcome.fail("Configuration data must be valid JSON", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check that no other jobs are running
        if (!noOtherJobsRunning()) {
            return EvaluationOutcome.fail("Another analytics job is currently running", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check external API availability
        if (!externalApiAvailable()) {
            return EvaluationOutcome.fail("External API is not available", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Email service availability check (simulated)
        if (!emailServiceAvailable()) {
            return EvaluationOutcome.fail("Email service is not available", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Analytics job schedule validation passed for job: {}", job.getJobId());
        return EvaluationOutcome.success();
    }

    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            logger.warn("Invalid JSON detected in configuration: {}", e.getMessage());
            return false;
        }
    }

    private boolean noOtherJobsRunning() {
        try {
            // Search for jobs in "running" state
            Condition runningCondition = Condition.lifecycle("state", "EQUALS", "running");
            SearchConditionRequest condition = SearchConditionRequest.group("AND", runningCondition);
            
            List<?> runningJobs = entityService.search(AnalyticsJob.class, condition);
            boolean noRunningJobs = runningJobs.isEmpty();
            
            if (!noRunningJobs) {
                logger.warn("Found {} running jobs, cannot start new job", runningJobs.size());
            }
            
            return noRunningJobs;
        } catch (Exception e) {
            logger.error("Failed to check for running jobs: {}", e.getMessage());
            // Assume no running jobs if we can't check (fail-safe)
            return true;
        }
    }

    private boolean externalApiAvailable() {
        try {
            String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Books";
            restTemplate.getForObject(apiUrl, String.class);
            logger.debug("External API is available");
            return true;
        } catch (Exception e) {
            logger.warn("External API is not available: {}", e.getMessage());
            return false;
        }
    }

    private boolean emailServiceAvailable() {
        // Simulate email service availability check
        // In a real implementation, this would check the actual email service
        try {
            // Simulate service check delay
            Thread.sleep(10);
            
            // Simulate 95% availability
            boolean available = Math.random() < 0.95;
            
            if (!available) {
                logger.warn("Email service is not available (simulated)");
            } else {
                logger.debug("Email service is available");
            }
            
            return available;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Email service check interrupted");
            return false;
        }
    }
}
