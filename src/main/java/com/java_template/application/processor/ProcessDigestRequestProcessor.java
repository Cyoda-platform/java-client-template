package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ProcessDigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public ProcessDigestRequestProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("ProcessDigestRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequest.class)
                .validate(this::isValidEntity, "Invalid DigestRequest entity")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ProcessDigestRequestProcessor".equals(modelSpec.operationName()) &&
                "digestRequest".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(DigestRequest entity) {
        return entity.getEmail() != null && !entity.getEmail().isEmpty();
    }

    private DigestRequest processEntityLogic(DigestRequest digestRequest) {
        logger.info("Processing DigestRequest event, technicalId={}", digestRequest.getId());

        String endpoint = "/pet/findByStatus";
        Map<String, Object> params = new HashMap<>();
        if (digestRequest.getMetadata() != null) {
            Object ep = digestRequest.getMetadata().get("endpoint");
            if (ep instanceof String) endpoint = (String) ep;
            Object p = digestRequest.getMetadata().get("params");
            if (p instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castParams = (Map<String, Object>) p;
                params = castParams;
            }
        }

        try {
            var webClient = org.springframework.web.reactive.function.client.WebClient.builder()
                    .baseUrl("https://petstore.swagger.io/v2")
                    .build();

            List<Object> apiResponse = webClient.get()
                    .uri(uriBuilder -> {
                        var ub = uriBuilder.path(endpoint);
                        params.forEach(ub::queryParam);
                        return ub.build();
                    })
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (apiResponse == null) {
                logger.error("External API returned null response");
                digestRequest.setStatus("FAILED_DATA_RETRIEVAL");
                digestRequest.setUpdatedAt(Instant.now());
                updateDigestRequestStatus(digestRequest);
                return digestRequest;
            }

            com.java_template.application.entity.DigestData digestData = new com.java_template.application.entity.DigestData();
            digestData.setDigestRequestId(digestRequest.getId());
            digestData.setDataPayload(apiResponse.toString());
            digestData.setRetrievedAt(Instant.now());

            java.util.concurrent.CompletableFuture<UUID> dataIdFuture = entityService.addItem(
                    "DigestData",
                    Config.ENTITY_VERSION,
                    digestData
            );
            UUID dataId = dataIdFuture.get();
            digestData.setId(dataId.toString());
            logger.info("Saved DigestData with id {}", dataId);

            digestRequest.setStatus("DATA_RETRIEVED");
            digestRequest.setUpdatedAt(Instant.now());
            updateDigestRequestStatus(digestRequest);

            // Trigger next processing step
            // In workflow this would be a transition to next state

        } catch (Exception e) {
            logger.error("Error fetching external data for DigestRequest id={}: {}", digestRequest.getId(), e.toString());
            digestRequest.setStatus("FAILED_DATA_RETRIEVAL");
            digestRequest.setUpdatedAt(Instant.now());
            updateDigestRequestStatus(digestRequest);
        }

        return digestRequest;
    }

    private void updateDigestRequestStatus(DigestRequest digestRequest) {
        try {
            entityService.updateItem(
                    "DigestRequest",
                    Config.ENTITY_VERSION,
                    UUID.fromString(digestRequest.getId()),
                    digestRequest
            ).get();
        } catch (Exception e) {
            logger.error("Failed to update DigestRequest status for id {}: {}", digestRequest.getId(), e.toString());
        }
    }

}
