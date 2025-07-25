package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
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
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class processDigestData implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public processDigestData(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("processDigestData initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestData.class)
                .validate(DigestData::isValid, "Invalid DigestData entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "processDigestData".equals(modelSpec.operationName()) &&
                "digestdata".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestData processEntityLogic(DigestData entity) {
        logger.info("Processing DigestData entity with digestRequestId: {}", entity.getDigestRequestId());

        String technicalId = entity.getDigestRequestId(); // Assuming digestRequestId as technicalId for update; adjust if needed
        try {
            entity.setStatus("PROCESSING");
            updateEntity("DigestData", technicalId, entity);

            RestTemplate restTemplate = new RestTemplate();
            String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

            String apiResponse = restTemplate.getForObject(apiUrl, String.class);
            if (apiResponse == null || apiResponse.isBlank()) {
                logger.error("Empty response from Petstore API for DigestData technicalId {}", technicalId);
                entity.setStatus("FAILED");
                updateEntity("DigestData", technicalId, entity);
                return entity;
            }

            entity.setApiData(apiResponse);
            entity.setStatus("SUCCESS");
            updateEntity("DigestData", technicalId, entity);

            EmailDispatch emailDispatch = new EmailDispatch();
            emailDispatch.setDigestRequestId(entity.getDigestRequestId());
            emailDispatch.setEmailContent("");
            emailDispatch.setStatus("PENDING");

            CompletableFuture<UUID> emailDispatchIdFuture = entityService.addItem("EmailDispatch", Config.ENTITY_VERSION, emailDispatch);
            UUID emailDispatchTechnicalId = emailDispatchIdFuture.get();

            processEmailDispatch(emailDispatchTechnicalId.toString(), emailDispatch);

        } catch (Exception e) {
            logger.error("Exception in processDigestData for technicalId {}: {}", technicalId, e.getMessage(), e);
            entity.setStatus("FAILED");
            updateEntity("DigestData", technicalId, entity);
        }

        return entity;
    }

    private void updateEntity(String entityModel, String technicalId, Object entity) {
        try {
            entityService.addItem(entityModel, Config.ENTITY_VERSION, entity);
        } catch (Exception e) {
            logger.error("Failed to update entityModel {} with technicalId {}: {}", entityModel, technicalId, e.getMessage(), e);
        }
    }

    private void processEmailDispatch(String technicalId, EmailDispatch emailDispatch) {
        // Placeholder for actual processEmailDispatch logic from prototype
        // Since no detailed logic provided, just log for now
        logger.info("Processing EmailDispatch with technicalId: {}", technicalId);
    }
}
