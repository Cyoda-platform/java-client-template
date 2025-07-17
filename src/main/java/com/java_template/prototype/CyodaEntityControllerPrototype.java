package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.RetrievedData;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/digest-request")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class DigestRequestDTO {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "status must be one of 'available','pending','sold'")
        private String status;
    }

    @PostMapping
    public Map<String, Object> addDigestRequest(@Valid @RequestBody DigestRequestDTO requestDto) throws ExecutionException, InterruptedException {
        log.info("Received DigestRequest POST: email={}, status={}", requestDto.getEmail(), requestDto.getStatus());
        DigestRequest digestRequest = new DigestRequest();
        digestRequest.setEmail(requestDto.getEmail());
        digestRequest.setStatus("Accepted");
        digestRequest.setCreatedAt(new Date());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("status", requestDto.getStatus());
        digestRequest.setMetadata(metadata);

        UUID technicalId = entityService.addItem("DigestRequest", ENTITY_VERSION, digestRequest).get();

        log.info("Saved DigestRequest entity with technicalId {}", technicalId);
        processDigestRequest(digestRequest, technicalId);

        Map<String, Object> response = new HashMap<>();
        response.put("digestRequestId", technicalId.toString());
        response.put("status", "Accepted");
        return response;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getDigestRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format for id " + id);
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId);
        ObjectNode digestRequestNode = itemFuture.get();
        if (digestRequestNode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest with id " + id + " not found");
        }

        DigestRequest dr = JsonNodeToDigestRequest(digestRequestNode);

        Map<String, Object> response = new HashMap<>();
        response.put("digestRequestId", id);
        response.put("email", dr.getEmail());
        response.put("status", dr.getStatus());

        DigestEmail email = getDigestEmailByDigestRequestId(id);
        response.put("digestContent", email != null && "Sent".equalsIgnoreCase(email.getStatus()) ? email.getContent() : null);
        return response;
    }

    private DigestEmail getDigestEmailByDigestRequestId(String digestRequestId) throws ExecutionException, InterruptedException {
        Condition cond = Condition.of("$.digestRequestId", "EQUALS", digestRequestId);
        SearchConditionRequest searchRequest = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("DigestEmail", ENTITY_VERSION, searchRequest);
        ArrayNode emailsNode = filteredItemsFuture.get();
        if (emailsNode == null || emailsNode.isEmpty()) {
            return null;
        }

        // Find last with status Sent ignoring case
        List<DigestEmail> emails = new ArrayList<>();
        for (int i = 0; i < emailsNode.size(); i++) {
            ObjectNode node = (ObjectNode) emailsNode.get(i);
            DigestEmail email = JsonNodeToDigestEmail(node);
            if ("Sent".equalsIgnoreCase(email.getStatus())) {
                emails.add(email);
            }
        }
        if (emails.isEmpty()) return null;
        return emails.get(emails.size() - 1);
    }

    private void processDigestRequest(DigestRequest digestRequest, UUID technicalId) {
        log.info("Processing DigestRequest event for technicalId {}", technicalId);
        String statusParam = "available";
        Map<String, Object> metadata = digestRequest.getMetadata();
        if (metadata != null && metadata.get("status") instanceof String) {
            statusParam = (String) metadata.get("status");
        }
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            RetrievedData retrievedData = new RetrievedData();
            retrievedData.setDigestRequestId(technicalId.toString());
            retrievedData.setDataPayload(response.getBody());
            retrievedData.setFetchedAt(new Date());

            UUID retrievedDataId = entityService.addItem("RetrievedData", ENTITY_VERSION, retrievedData).get();
            log.info("Saved RetrievedData entity with technicalId {}", retrievedDataId);

            // Update DigestRequest status to DataFetched
            digestRequest.setStatus("DataFetched");
            entityService.updateItem("DigestRequest", ENTITY_VERSION, technicalId, digestRequest).get();

            // Process RetrievedData
            processRetrievedData(retrievedData, retrievedDataId);

        } catch (Exception e) {
            log.error("Error fetching data from external API: {}", e.getMessage(), e);
            try {
                digestRequest.setStatus("Failed");
                entityService.updateItem("DigestRequest", ENTITY_VERSION, technicalId, digestRequest).get();
            } catch (Exception ex) {
                log.error("Error updating DigestRequest status to Failed: {}", ex.getMessage(), ex);
            }
        }
    }

    private void processRetrievedData(RetrievedData retrievedData, UUID technicalId) throws ExecutionException, InterruptedException {
        log.info("Processing RetrievedData event for technicalId {}", technicalId);
        String compiledContent = "<html><body><h3>Petstore Data Digest</h3><pre>" +
            escapeHtml(retrievedData.getDataPayload()) +
            "</pre></body></html>";
        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(retrievedData.getDigestRequestId());
        digestEmail.setContent(compiledContent);
        digestEmail.setStatus("Ready");

        UUID digestEmailId = entityService.addItem("DigestEmail", ENTITY_VERSION, digestEmail).get();
        log.info("Saved DigestEmail entity with technicalId {}", digestEmailId);

        DigestRequest dr = getDigestRequestEntityByTechnicalId(UUID.fromString(retrievedData.getDigestRequestId()));
        if (dr != null) {
            dr.setStatus("DigestCompiled");
            entityService.updateItem("DigestRequest", ENTITY_VERSION, UUID.fromString(retrievedData.getDigestRequestId()), dr).get();
        }
    }

    private DigestRequest getDigestRequestEntityByTechnicalId(UUID technicalId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId);
        ObjectNode digestRequestNode = itemFuture.get();
        if (digestRequestNode == null) return null;
        return JsonNodeToDigestRequest(digestRequestNode);
    }

    private void processDigestEmail(DigestEmail digestEmail, UUID technicalId) throws ExecutionException, InterruptedException {
        log.info("Processing DigestEmail event for technicalId {}", technicalId);
        DigestRequest dr = getDigestRequestEntityByTechnicalId(UUID.fromString(digestEmail.getDigestRequestId()));
        if (dr == null) {
            log.error("DigestRequest not found for DigestEmail technicalId {}", technicalId);
            return;
        }
        log.info("Mock sending email to {} with digest content length {}", dr.getEmail(), digestEmail.getContent().length());
        digestEmail.setStatus("Sent");
        digestEmail.setSentAt(new Date());
        entityService.updateItem("DigestEmail", ENTITY_VERSION, technicalId, digestEmail).get();

        dr.setStatus("Completed");
        entityService.updateItem("DigestRequest", ENTITY_VERSION, UUID.fromString(digestEmail.getDigestRequestId()), dr).get();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }

    private DigestRequest JsonNodeToDigestRequest(ObjectNode node) {
        DigestRequest dr = new DigestRequest();
        if (node.has("email")) dr.setEmail(node.get("email").asText());
        if (node.has("status")) dr.setStatus(node.get("status").asText());
        if (node.has("createdAt")) dr.setCreatedAt(new Date(node.get("createdAt").asLong(0)));
        if (node.has("metadata") && node.get("metadata").isObject()) {
            Map<String, Object> metadata = new HashMap<>();
            node.get("metadata").fields().forEachRemaining(e -> metadata.put(e.getKey(), e.getValue().asText()));
            dr.setMetadata(metadata);
        }
        // id field is not used, use technicalId externally
        return dr;
    }

    private DigestEmail JsonNodeToDigestEmail(ObjectNode node) {
        DigestEmail email = new DigestEmail();
        if (node.has("digestRequestId")) email.setDigestRequestId(node.get("digestRequestId").asText());
        if (node.has("content")) email.setContent(node.get("content").asText());
        if (node.has("status")) email.setStatus(node.get("status").asText());
        if (node.has("sentAt")) email.setSentAt(new Date(node.get("sentAt").asLong(0)));
        return email;
    }

    // Additional endpoints to allow processing DigestEmail for completeness (if needed)
    @PostMapping("/digest-email/process/{id}")
    public void processDigestEmailById(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DigestEmail", ENTITY_VERSION, technicalId);
        ObjectNode emailNode = itemFuture.get();
        if (emailNode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestEmail with id " + id + " not found");
        }
        DigestEmail email = JsonNodeToDigestEmail(emailNode);
        processDigestEmail(email, technicalId);
    }
}