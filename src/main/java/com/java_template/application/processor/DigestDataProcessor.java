package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DigestDataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DigestDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("DigestDataProcessor initialized with SerializerFactory, EntityService and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(DigestData.class)
            .validate(DigestData::isValid, "Invalid DigestData entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestDataProcessor".equals(modelSpec.operationName()) &&
               "digestData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestData processEntityLogic(DigestData entity) {
        try {
            UUID digestDataId = UUID.fromString(entity.getJobTechnicalId());
            logger.info("Processing DigestData with ID: {}", digestDataId);

            // Compile retrieved data into digest content
            String compiledContent = compileDigestContent(entity.getRetrievedData(), entity.getFormat());

            // Create DigestEmail entity
            DigestEmail digestEmail = new DigestEmail();
            digestEmail.setJobTechnicalId(entity.getJobTechnicalId());

            // Retrieve corresponding DigestRequestJob to get email
            CompletableFuture<ObjectNode> jobFuture = entityService.getItem("digestRequestJob", Config.ENTITY_VERSION, digestDataId);
            ObjectNode jobNode = jobFuture.get();
            if (jobNode != null) {
                DigestRequestJob job = objectMapper.treeToValue(jobNode, DigestRequestJob.class);
                digestEmail.setEmail(job.getEmail());
            }

            digestEmail.setContent(compiledContent);
            digestEmail.setSentAt(null);
            digestEmail.setStatus("PENDING");

            CompletableFuture<UUID> emailIdFuture = entityService.addItem("digestEmail", Config.ENTITY_VERSION, digestEmail);
            UUID digestEmailId = emailIdFuture.get();

            logger.info("DigestEmail created with ID: {}", digestEmailId);

            processDigestEmail(digestEmailId, digestEmail);

        } catch (Exception e) {
            logger.error("Error processing DigestData: {}", e.getMessage());
        }
        return entity;
    }

    private String compileDigestContent(String rawData, String format) {
        if ("HTML".equalsIgnoreCase(format)) {
            return "<html><body><pre>" + rawData + "</pre></body></html>";
        } else {
            return rawData;
        }
    }

    private void processDigestEmail(UUID digestEmailId, DigestEmail digestEmail) {
        // This method is expected to be implemented elsewhere or could be a trigger for another processor
        logger.info("Processing DigestEmail with ID: {}", digestEmailId);
        // Actual sending logic is not implemented here as it might be handled by another processor
    }

}
