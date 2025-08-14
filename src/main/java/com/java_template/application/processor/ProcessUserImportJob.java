package com.java_template.application.processor;

import com.java_template.application.entity.userimportjob.version_1.UserImportJob;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessUserImportJob implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessUserImportJob.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessUserImportJob(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UserImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(UserImportJob.class)
            .validate(this::isValidEntity, "Invalid UserImportJob entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(UserImportJob job) {
        if (job == null) return false;
        if (job.getImportData() == null || job.getImportData().trim().isEmpty()) return false;
        if (job.getStatus() == null) return false;
        return true;
    }

    private UserImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserImportJob> context) {
        UserImportJob job = context.entity();
        logger.info("Start processing UserImportJob with ID: {}", job.getJobId());

        // Business logic:
        // 1. Validate importData format
        // 2. Parse importData JSON to User list
        // 3. Create User entities (immutable)
        // 4. Update job status to COMPLETED or FAILED

        try {
            List<User> users = objectMapper.readValue(job.getImportData(), new TypeReference<List<User>>() {});
            // Here you would typically persist users to the database
            logger.info("Parsed {} users from importData.", users.size());
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Failed to parse importData: {}", e.getMessage());
            job.setStatus("FAILED");
        }

        return job;
    }
}
