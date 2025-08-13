package com.java_template.application.processor;

import com.java_template.application.entity.userimportjob.version_1.UserImportJob;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserImportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UserImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(UserImportJob.class)
            .validate(this::isValidEntity, "Invalid UserImportJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(UserImportJob job) {
        if (job == null) {
            logger.error("UserImportJob entity is null");
            return false;
        }
        if (job.getImportData() == null || job.getImportData().trim().isEmpty()) {
            logger.error("Import data is empty");
            return false;
        }
        if (!"pending".equalsIgnoreCase(job.getStatus())) {
            logger.error("UserImportJob status is not pending: {}", job.getStatus());
            return false;
        }
        return true;
    }

    private UserImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserImportJob> context) {
        UserImportJob job = context.entity();
        try {
            List<User> users = objectMapper.readValue(job.getImportData(), new TypeReference<List<User>>() {});
            // TODO: Persist each User entity as immutable
            logger.info("Parsed {} users from import data", users.size());
            job.setStatus("processing");
        } catch (Exception e) {
            logger.error("Failed to parse import data", e);
            job.setStatus("failed");
        }
        return job;
    }
}
