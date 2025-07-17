package com.java_template.prototype;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping(path = "/prototype/api/digest-request")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_DIGEST_REQUEST = "DigestRequest";
    private static final String ENTITY_DIGEST_DATA = "DigestData";
    private static final String ENTITY_DIGEST_EMAIL = "DigestEmail";

    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateResponse createDigestRequest(@RequestBody @Valid DigestRequestCreateRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received digest request creation for email {}", request.getEmail());

        DigestRequest entity = new DigestRequest();
        entity.setEmail(request.getEmail());
        entity.setMetadata(request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap());
        entity.setStatus("CREATED");
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_DIGEST_REQUEST,
                ENTITY_VERSION,
                entity
        );
        UUID technicalId = idFuture.get();
        logger.info("Saved DigestRequest with technicalId {}", technicalId);

        entity.setTechnicalId(technicalId);

        processDigestRequest(entity);

        return new CreateResponse(technicalId.toString(), entity.getStatus());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestRequest getDigestRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_DIGEST_REQUEST,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode obj = itemFuture.get();
        if (obj == null || obj.isEmpty()) {
            throw notFound(ENTITY_DIGEST_REQUEST, id);
        }
        DigestRequest entity = JsonUtil.convertObjectNodeToEntity(obj, DigestRequest.class);
        entity.setTechnicalId(technicalId);
        return entity;
    }

    @DeleteMapping(path = "/{id}")
    public void deleteDigestRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                ENTITY_DIGEST_REQUEST,
                ENTITY_VERSION,
                technicalId
        );
        UUID deletedId = deletedIdFuture.get();
        if (!technicalId.equals(deletedId)) {
            throw notFound(ENTITY_DIGEST_REQUEST, id);
        }
        logger.info("Deleted DigestRequest with technicalId {}", id);
    }

    @GetMapping(path = "/digest-data/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestData getDigestData(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_DIGEST_DATA,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode obj = itemFuture.get();
        if (obj == null || obj.isEmpty()) {
            throw notFound(ENTITY_DIGEST_DATA, id);
        }
        DigestData entity = JsonUtil.convertObjectNodeToEntity(obj, DigestData.class);
        entity.setTechnicalId(technicalId);
        return entity;
    }

    @GetMapping(path = "/digest-emails/{digestRequestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestEmail getDigestEmail(@PathVariable @NotBlank String digestRequestId) throws ExecutionException, InterruptedException {
        UUID digestRequestTechnicalId = UUID.fromString(digestRequestId);
        Condition cond = Condition.of("$.digestRequestId", "EQUALS", digestRequestId);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> emailsFuture = entityService.getItemsByCondition(
                ENTITY_DIGEST_EMAIL,
                ENTITY_VERSION,
                searchCondition
        );
        ArrayNode emailsArray = emailsFuture.get();
        if (emailsArray == null || emailsArray.isEmpty()) {
            throw notFound("DigestEmail for DigestRequest", digestRequestId);
        }
        ObjectNode emailNode = (ObjectNode) emailsArray.get(0);
        DigestEmail email = JsonUtil.convertObjectNodeToEntity(emailNode, DigestEmail.class);
        UUID technicalId = UUID.fromString(emailNode.get("technicalId").asText());
        email.setTechnicalId(technicalId);
        return email;
    }

    @PostMapping(path = "/digest-emails/{digestRequestId}/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public SendResponse sendDigestEmail(@PathVariable @NotBlank String digestRequestId) throws ExecutionException, InterruptedException {
        DigestEmail email = getDigestEmail(digestRequestId);
        logger.info("Send triggered for DigestEmail technicalId={} linked to DigestRequest id={}", email.getTechnicalId(), digestRequestId);

        email.setSentStatus("SENT");
        email.setSentAt(Instant.now());

        CompletableFuture<UUID> updateFuture = entityService.updateItem(
                ENTITY_DIGEST_EMAIL,
                ENTITY_VERSION,
                email.getTechnicalId(),
                email
        );
        UUID updatedId = updateFuture.get();
        logger.info("DigestEmail sent successfully for DigestRequest id={}, updated technicalId={}", digestRequestId, updatedId);

        return new SendResponse(email.getSentStatus());
    }

    private void processDigestRequest(DigestRequest digestRequest) {
        logger.info("Processing DigestRequest event, technicalId={}", digestRequest.getTechnicalId());

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
            // External API call (as in original)
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
                return;
            }

            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(digestRequest.getTechnicalId().toString());
            digestData.setDataPayload(apiResponse);
            digestData.setRetrievedAt(Instant.now());

            CompletableFuture<UUID> dataIdFuture = entityService.addItem(
                    ENTITY_DIGEST_DATA,
                    ENTITY_VERSION,
                    digestData
            );
            UUID dataId = dataIdFuture.get();
            digestData.setTechnicalId(dataId);
            logger.info("Saved DigestData with technicalId {}", dataId);

            digestRequest.setStatus("DATA_RETRIEVED");
            digestRequest.setUpdatedAt(Instant.now());
            updateDigestRequestStatus(digestRequest);

            processDigestData(digestData);

        } catch (Exception e) {
            logger.error("Error fetching external data for DigestRequest technicalId={}: {}", digestRequest.getTechnicalId(), e.toString());
            digestRequest.setStatus("FAILED_DATA_RETRIEVAL");
            digestRequest.setUpdatedAt(Instant.now());
            updateDigestRequestStatus(digestRequest);
        }
    }

    private void updateDigestRequestStatus(DigestRequest digestRequest) {
        try {
            entityService.updateItem(
                    ENTITY_DIGEST_REQUEST,
                    ENTITY_VERSION,
                    digestRequest.getTechnicalId(),
                    digestRequest
            ).get();
        } catch (Exception e) {
            logger.error("Failed to update DigestRequest status for technicalId {}: {}", digestRequest.getTechnicalId(), e.toString());
        }
    }

    private void processDigestData(DigestData digestData) {
        logger.info("Processing DigestData event, technicalId={}", digestData.getTechnicalId());

        StringBuilder emailContent = new StringBuilder();
        emailContent.append("<html><body><h3>Your Digest Data</h3><pre>")
                .append(digestData.getDataPayload().toString())
                .append("</pre></body></html>");

        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(digestData.getDigestRequestId());
        digestEmail.setEmailContent(emailContent.toString());
        digestEmail.setSentStatus("PENDING");

        try {
            CompletableFuture<UUID> emailIdFuture = entityService.addItem(
                    ENTITY_DIGEST_EMAIL,
                    ENTITY_VERSION,
                    digestEmail
            );
            UUID emailId = emailIdFuture.get();
            digestEmail.setTechnicalId(emailId);
            logger.info("Saved DigestEmail with technicalId {}", emailId);

            processDigestEmail(digestEmail);
        } catch (Exception e) {
            logger.error("Failed to save DigestEmail: {}", e.toString());
        }
    }

    private void processDigestEmail(DigestEmail digestEmail) {
        logger.info("Processing DigestEmail event, technicalId={}", digestEmail.getTechnicalId());
        digestEmail.setSentStatus("SENT");
        digestEmail.setSentAt(Instant.now());
        try {
            entityService.updateItem(
                    ENTITY_DIGEST_EMAIL,
                    ENTITY_VERSION,
                    digestEmail.getTechnicalId(),
                    digestEmail
            ).get();
            logger.info("DigestEmail technicalId={} marked as SENT (prototype)", digestEmail.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to update DigestEmail sent status for technicalId {}: {}", digestEmail.getTechnicalId(), e.toString());
        }
    }

    private ResponseStatusException notFound(String entityName, String id) {
        String msg = entityName + " with id " + id + " not found";
        logger.error(msg);
        return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, msg);
    }

    @Data
    public static class DigestRequestCreateRequest {
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is mandatory")
        private String email;
        private Map<String, Object> metadata;
    }

    @Data
    public static class CreateResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class SendResponse {
        private final String sentStatus;
    }

    static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convertObjectNodeToEntity(ObjectNode node, Class<T> clazz) {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.error("Failed to convert ObjectNode to {}: {}", clazz.getSimpleName(), e.toString());
                throw new RuntimeException(e);
            }
        }
    }
}