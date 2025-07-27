package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.client.RestTemplate;

@Component
public class DigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final RestTemplate restTemplate;

    public DigestRequestProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
        logger.info("DigestRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequest.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(DigestRequest entity) {
        return entity != null && entity.isValid();
    }

    private DigestRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DigestRequest> context) {
        DigestRequest entity = context.entity();
        String technicalId = context.request().getEntityId();

        logger.info("Processing DigestRequest with id: {}", technicalId);
        try {
            if (!entity.isValid()) {
                logger.error("DigestRequest {} validation failed", technicalId);
                entity.setStatus("FAILED");
                return entity;
            }

            entity.setStatus("PROCESSING");

            String baseUrl = "https://petstore.swagger.io/v2";
            String endpoint = entity.getExternalApiEndpoint();
            String url = baseUrl + endpoint;

            logger.info("Calling external API: {}", url);

            var response = restTemplate.getForEntity(url, String.class);
            String apiResponse = response.getBody();

            if (apiResponse == null || apiResponse.isBlank()) {
                logger.error("Empty response from external API for DigestRequest {}", technicalId);
                entity.setStatus("FAILED");
                return entity;
            }

            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(technicalId);
            digestData.setRetrievedData(apiResponse);
            digestData.setProcessedTimestamp(Instant.now());

            CompletableFuture<UUID> digestDataIdFuture = entityService.addItem(
                    "DigestData",
                    Config.ENTITY_VERSION,
                    digestData
            );
            UUID digestDataId = digestDataIdFuture.get();
            logger.info("Persisted DigestData with id: {}", digestDataId.toString());

            entity.setStatus("COMPLETED");

            processEmailDispatch(technicalId, entity, digestData);

        } catch (Exception e) {
            logger.error("Error while retrieving data for DigestRequest {}: {}", technicalId, e.getMessage());
            entity.setStatus("FAILED");
        }

        return entity;
    }

    private void processEmailDispatch(String digestRequestId, DigestRequest request, DigestData digestData) {
        logger.info("Processing EmailDispatch for DigestRequest id: {}", digestRequestId);

        EmailDispatch emailDispatch = new EmailDispatch();
        emailDispatch.setDigestRequestId(digestRequestId);

        String emailContent = "<html><body><h3>Digest Data</h3><pre>" + digestData.getRetrievedData() + "</pre></body></html>";
        emailDispatch.setEmailContent(emailContent);
        emailDispatch.setDispatchTimestamp(Instant.now());
        emailDispatch.setStatus("PENDING");

        try {
            CompletableFuture<UUID> emailDispatchIdFuture = entityService.addItem(
                    "EmailDispatch",
                    Config.ENTITY_VERSION,
                    emailDispatch
            );
            UUID emailDispatchId = emailDispatchIdFuture.get();
            logger.info("Persisted EmailDispatch with id: {}", emailDispatchId.toString());

            logger.info("Sending email to {}", request.getUserEmail());
            Thread.sleep(500);

            emailDispatch.setStatus("SENT");
            logger.info("Email sent successfully for DigestRequest id: {}", digestRequestId);

        } catch (Exception e) {
            logger.error("Failed to send email for DigestRequest {}: {}", digestRequestId, e.getMessage());
            emailDispatch.setStatus("FAILED");
        }
    }

}
