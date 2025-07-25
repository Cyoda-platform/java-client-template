package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
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
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class processDigestData implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public processDigestData(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("processDigestData initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(DigestData.class)
            .validate(DigestData::isValid, "Invalid DigestData entity")
            .map(this::processDigestDataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "processDigestData".equals(modelSpec.operationName()) &&
               "digestData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestData processDigestDataLogic(DigestData entity) {
        // Business logic: compile retrieved data into HTML digest, persist DigestEmail with PENDING status
        try {
            String retrievedData = entity.getRetrievedData();
            if (retrievedData == null || retrievedData.isBlank()) {
                logger.warn("No retrieved data to process for DigestData id: {}", entity.getTechnicalId());
                return entity;
            }

            // Simple compilation logic: wrap retrievedData in HTML tags
            String htmlContent = "<html><body><pre>" + retrievedData + "</pre></body></html>";

            com.java_template.application.entity.DigestEmail digestEmail = new com.java_template.application.entity.DigestEmail();
            digestEmail.setDigestRequestId(entity.getDigestRequestId());
            digestEmail.setEmailContent(htmlContent);
            digestEmail.setStatus("PENDING");

            CompletableFuture<UUID> future = entityService.addItem("digestEmail", Config.ENTITY_VERSION, digestEmail);
            future.get(); // wait for completion

            // After saving DigestEmail, trigger processDigestEmail (event-driven, not shown here)

        } catch (Exception e) {
            logger.error("Error processing DigestData: {}", e.getMessage(), e);
        }

        return entity;
    }
}
