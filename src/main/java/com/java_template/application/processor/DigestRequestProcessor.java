package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class DigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final HttpClient httpClient;
    private final com.java_template.common.service.EntityService entityService;

    public DigestRequestProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.httpClient = HttpClient.newHttpClient();
        logger.info("DigestRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequest.class)
                .validate(DigestRequest::isValid, "Invalid DigestRequest entity")
                .map(this::processDigestRequest)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestProcessor".equals(modelSpec.operationName()) &&
                "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequest processDigestRequest(DigestRequest entity) {
        logger.info("Processing DigestRequest with ID: {}", entity.getId());
        try {
            entity.setStatus(DigestRequest.StatusEnum.PROCESSING);
            entity.setUpdatedAt(LocalDateTime.now());
            entityService.updateItem("DigestRequest", Config.ENTITY_VERSION, UUID.fromString(entity.getId()), entity).join();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(entity.getExternalApiUrl()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                logger.info("External API call successful for DigestRequest ID {}: Response length: {}", entity.getId(), responseBody.length());

                // Process responseBody, format email content using template if needed
                // Send email to recipients - integrate with email service

                entity.setStatus(DigestRequest.StatusEnum.COMPLETED);
            } else {
                logger.error("External API call failed for DigestRequest ID {} with status code {}", entity.getId(), response.statusCode());
                entity.setStatus(DigestRequest.StatusEnum.FAILED);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Exception during external API call for DigestRequest ID {}: {}", entity.getId(), e.getMessage());
            entity.setStatus(DigestRequest.StatusEnum.FAILED);
            Thread.currentThread().interrupt();
        }

        entity.setUpdatedAt(LocalDateTime.now());
        entityService.updateItem("DigestRequest", Config.ENTITY_VERSION, UUID.fromString(entity.getId()), entity).join();
        logger.info("Completed processing DigestRequest with ID: {}. Status: {}", entity.getId(), entity.getStatus());
        return entity;
    }
}
