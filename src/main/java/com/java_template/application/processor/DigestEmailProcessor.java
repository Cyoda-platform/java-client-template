package com.java_template.application.processor;

import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequestJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ErrorInfo;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.java_template.common.service.EntityService;

@Component
public class DigestEmailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DigestEmailProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("DigestEmailProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestEmail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestEmail.class)
            .validate(DigestEmail::isValid, "Invalid DigestEmail entity")
            .map(this::processDigestEmailLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestEmailProcessor".equals(modelSpec.operationName()) &&
               "digestEmail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestEmail processDigestEmailLogic(DigestEmail email) {
        logger.info("Processing DigestEmail with JobTechnicalId: {}", email.getJobTechnicalId());

        try {
            // Simulate email sending - replace with real email sending logic in production
            logger.info("Sending email to: {}", email.getEmail());
            // Simulated send success
            email.setStatus("SENT");
            email.setSentAt(Instant.now());

            entityService.addItem("digestEmail", Config.ENTITY_VERSION, email).join();

            // Update related job status to COMPLETED
            UUID jobId = UUID.fromString(email.getJobTechnicalId());
            CompletableFuture<ObjectNode> jobFuture = entityService.getItem("digestRequestJob", Config.ENTITY_VERSION, jobId);
            ObjectNode jobNode = jobFuture.get();
            if (jobNode != null) {
                DigestRequestJob job = objectMapper.treeToValue(jobNode, DigestRequestJob.class);
                job.setStatus("COMPLETED");
                entityService.addItem("digestRequestJob", Config.ENTITY_VERSION, job).join();
            }
            logger.info("Email sent successfully to: {}", email.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send email: {}", e.getMessage());
            email.setStatus("FAILED");
            entityService.addItem("digestEmail", Config.ENTITY_VERSION, email).join();

            try {
                UUID jobId = UUID.fromString(email.getJobTechnicalId());
                CompletableFuture<ObjectNode> jobFuture = entityService.getItem("digestRequestJob", Config.ENTITY_VERSION, jobId);
                ObjectNode jobNode = jobFuture.get();
                if (jobNode != null) {
                    DigestRequestJob job = objectMapper.treeToValue(jobNode, DigestRequestJob.class);
                    job.setStatus("FAILED");
                    entityService.addItem("digestRequestJob", Config.ENTITY_VERSION, job).join();
                }
            } catch (Exception ex) {
                logger.error("Failed to update job status to FAILED: {}", ex.getMessage());
            }
        }

        return email;
    }

}
