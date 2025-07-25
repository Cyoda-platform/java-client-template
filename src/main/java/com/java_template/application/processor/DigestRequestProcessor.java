package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final com.java_template.common.service.EntityService entityService;

    public DigestRequestProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        logger.info("DigestRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequest.class)
                .validate(DigestRequest::isValid)
                .map(this::processDigestRequestLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestProcessor".equals(modelSpec.operationName()) &&
               "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequest processDigestRequestLogic(DigestRequest request) {
        logger.info("Processing DigestRequest entity");

        if (!request.getEmail().matches("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$")) {
            logger.error("Invalid email format for DigestRequest: {}", request.getEmail());
            return request; // invalid email, just return entity unchanged
        }

        String endpoint = "/pet/findByStatus";
        String params = "status=available";

        if (request.getRequestPayload() != null && !request.getRequestPayload().isBlank()) {
            try {
                JsonNode jsonNode = objectMapper.readTree(request.getRequestPayload());
                if (jsonNode.has("endpoint")) {
                    endpoint = jsonNode.get("endpoint").asText();
                }
                if (jsonNode.has("params")) {
                    JsonNode paramsNode = jsonNode.get("params");
                    List<String> paramPairs = new ArrayList<>();
                    paramsNode.fieldNames().forEachRemaining(field -> {
                        paramPairs.add(field + "=" + paramsNode.get(field).asText());
                    });
                    params = String.join("&", paramPairs);
                }
            } catch (Exception ex) {
                logger.error("Failed to parse requestPayload: {}", ex.getMessage());
            }
        }

        String url = "https://petstore.swagger.io/v2" + endpoint + "?" + params;

        try {
            String responseBody = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class).getBody();

            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(request.getEmail()); // Use email as ID reference (no technicalId in entity)
            digestData.setRetrievedData(responseBody);
            digestData.setFormatType("html");

            CompletableFuture<UUID> digestDataIdFuture = entityService.addItem("digestData", Config.ENTITY_VERSION, digestData);
            UUID digestDataId = digestDataIdFuture.get();

            logger.info("DigestData saved with ID: {}", digestDataId);

            // Trigger additional processing for DigestData
            processDigestData(digestDataId.toString(), digestData);

        } catch (Exception e) {
            logger.error("External API call failed: {}", e.getMessage());
        }

        return request;
    }

    private void processDigestData(String digestDataId, DigestData digestData) {
        logger.info("Processing DigestData with ID: {}", digestDataId);

        String compiledHtml = "<html><body><h1>Digest Data</h1><pre>" + digestData.getRetrievedData() + "</pre></body></html>";

        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(digestData.getDigestRequestId());
        digestEmail.setEmailContent(compiledHtml);
        digestEmail.setStatus("PENDING");

        try {
            CompletableFuture<UUID> digestEmailIdFuture = entityService.addItem("digestEmail", Config.ENTITY_VERSION, digestEmail);
            UUID digestEmailId = digestEmailIdFuture.get();

            logger.info("DigestEmail saved with ID: {}", digestEmailId);

            processDigestEmail(digestEmailId.toString(), digestEmail);

        } catch (Exception e) {
            logger.error("Failed to save DigestEmail: {}", e.getMessage());
        }
    }

    private void processDigestEmail(String digestEmailId, DigestEmail digestEmail) {
        logger.info("Processing DigestEmail with ID: {}", digestEmailId);

        try {
            UUID digestRequestUUID = UUID.fromString(digestEmail.getDigestRequestId());
            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> requestFuture = entityService.getItem("digestRequest", Config.ENTITY_VERSION, digestRequestUUID);
            com.fasterxml.jackson.databind.node.ObjectNode requestNode = requestFuture.get();
            if (requestNode == null || requestNode.isEmpty()) {
                logger.error("Recipient email not found for DigestEmail ID: {}", digestEmailId);
                digestEmail.setStatus("FAILED");
                return;
            }
            DigestRequest digestRequest = objectMapper.treeToValue(requestNode, DigestRequest.class);
            String recipient = digestRequest.getEmail();
            if (recipient == null || recipient.isBlank()) {
                logger.error("Recipient email missing for DigestEmail ID: {}", digestEmailId);
                digestEmail.setStatus("FAILED");
                return;
            }

            // Simulate sending email
            logger.info("Sending digest email to: {}", recipient);
            logger.info("Email content: {}", digestEmail.getEmailContent());
            digestEmail.setStatus("SENT");
            logger.info("DigestEmail ID: {} sent successfully.", digestEmailId);
        } catch (Exception e) {
            logger.error("Failed to send DigestEmail ID: {}: {}", digestEmailId, e.getMessage());
            digestEmail.setStatus("FAILED");
        }
    }
}
