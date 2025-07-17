package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype/digestRequest")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_MODEL = "DigestRequest";

    // ====== DIGEST REQUEST DTO for validation ======
    @Data
    public static class DigestRequestDto {
        @NotBlank
        @Size(max = 100)
        private String userEmail;

        @NotNull
        private Map<@NotBlank String, @NotBlank String> metadata;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDigestRequest(@RequestBody @Valid DigestRequestDto dto) throws ExecutionException, InterruptedException {
        logger.info("Received request to create DigestRequest: {}", dto);

        DigestRequest entity = new DigestRequest();
        entity.setUserEmail(dto.getUserEmail());
        entity.setMetadata(dto.getMetadata());

        if (!entity.isValid()) {
            logger.error("Invalid DigestRequest data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                entity
        );
        UUID technicalId = idFuture.get();

        // Set id as string of technicalId for response consistency
        String id = technicalId.toString();

        processDigestRequest(entity);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "created");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DigestRequest>> listDigestRequests() throws ExecutionException, InterruptedException {
        logger.info("Fetching all DigestRequests");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                ENTITY_MODEL,
                ENTITY_VERSION
        );
        ArrayNode arrayNode = itemsFuture.get();

        List<DigestRequest> result = new ArrayList<>();
        for (int i = 0; i < arrayNode.size(); i++) {
            ObjectNode obj = (ObjectNode) arrayNode.get(i);
            DigestRequest entity = JsonUtil.convertObjectNodeToObject(obj, DigestRequest.class);
            // Set id as string form of technicalId
            if (obj.has("technicalId")) {
                entity.setId(obj.get("technicalId").asText());
            }
            result.add(entity);
        }
        return ResponseEntity.ok(Collections.unmodifiableList(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DigestRequest> getDigestRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Fetching DigestRequest with id {}", id);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format for id");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode obj = itemFuture.get();
        if (obj == null || obj.isEmpty()) {
            logger.error("DigestRequest with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        DigestRequest entity = JsonUtil.convertObjectNodeToObject(obj, DigestRequest.class);
        entity.setId(id);
        return ResponseEntity.ok(entity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateDigestRequest(@PathVariable @NotBlank String id,
                                                                   @RequestBody @Valid DigestRequestDto dto) throws ExecutionException, InterruptedException {
        logger.info("Updating DigestRequest with id {}", id);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format for id");
        }

        DigestRequest entity = new DigestRequest();
        entity.setUserEmail(dto.getUserEmail());
        entity.setMetadata(dto.getMetadata());

        if (!entity.isValid()) {
            logger.error("Invalid DigestRequest data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest data");
        }

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                technicalId,
                entity
        );

        UUID updatedId = updatedItemId.get();
        if (updatedId == null) {
            logger.error("DigestRequest with id {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }

        processDigestRequest(entity);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "updated");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDigestRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Deleting DigestRequest with id {}", id);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format for id");
        }

        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                technicalId
        );
        UUID deletedId = deletedItemId.get();
        if (deletedId == null) {
            logger.error("DigestRequest with id {} not found for delete", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "deleted");
        return ResponseEntity.ok(response);
    }

    private void processDigestRequest(DigestRequest entity) {
        logger.info("Simulating processing of DigestRequest technicalId={} for userEmail={}", entity.getId(), entity.getUserEmail());
        // TODO: Add real event processing logic here or call Cyoda workflow trigger
    }

    // ====== EMAIL DISPATCH CRUD (keep local cache as minor entity) ======

    @RestController
    @RequestMapping(path = "/prototype/emailDispatch")
    public static class EmailDispatchController {

        private static final Logger logger = LoggerFactory.getLogger(EmailDispatchController.class);

        private final Map<String, List<EmailDispatch>> emailDispatchCache = new HashMap<>();
        private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

        @Data
        public static class EmailDispatchDto {
            @NotBlank
            private String status;

            @Size(max = 500)
            private String detail;
        }

        @PostMapping
        public ResponseEntity<Map<String, Object>> createEmailDispatch(@RequestBody @Valid EmailDispatchDto dto) {
            logger.info("Received request to create EmailDispatch: {}", dto);

            EmailDispatch entity = new EmailDispatch();
            entity.setStatus(dto.getStatus());
            entity.setDetail(dto.getDetail());

            if (!entity.isValid()) {
                logger.error("Invalid EmailDispatch data");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid EmailDispatch data");
            }

            String id = addEmailDispatch(entity);
            logger.info("EmailDispatch created with id {}", id);

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "created");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping
        public ResponseEntity<List<EmailDispatch>> listEmailDispatch() {
            logger.info("Fetching all EmailDispatch");
            List<EmailDispatch> list = emailDispatchCache.getOrDefault("entities", Collections.emptyList());
            return ResponseEntity.ok(Collections.unmodifiableList(list));
        }

        @GetMapping("/{id}")
        public ResponseEntity<EmailDispatch> getEmailDispatch(@PathVariable @NotBlank String id) {
            logger.info("Fetching EmailDispatch with id {}", id);
            EmailDispatch ed = getEmailDispatchById(id);
            if (ed == null) {
                logger.error("EmailDispatch with id {} not found", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
            }
            return ResponseEntity.ok(ed);
        }

        @PutMapping("/{id}")
        public ResponseEntity<Map<String, Object>> updateEmailDispatch(@PathVariable @NotBlank String id,
                                                                       @RequestBody @Valid EmailDispatchDto dto) {
            logger.info("Updating EmailDispatch with id {}", id);

            EmailDispatch entity = new EmailDispatch();
            entity.setStatus(dto.getStatus());
            entity.setDetail(dto.getDetail());

            if (!entity.isValid()) {
                logger.error("Invalid EmailDispatch data");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid EmailDispatch data");
            }

            boolean updated = updateEmailDispatchById(id, entity);
            if (!updated) {
                logger.error("EmailDispatch with id {} not found for update", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "updated");
            return ResponseEntity.ok(response);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Map<String, Object>> deleteEmailDispatch(@PathVariable @NotBlank String id) {
            logger.info("Deleting EmailDispatch with id {}", id);
            boolean deleted = deleteEmailDispatchById(id);
            if (!deleted) {
                logger.error("EmailDispatch with id {} not found for delete", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
            }
            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "deleted");
            return ResponseEntity.ok(response);
        }

        private String addEmailDispatch(EmailDispatch entity) {
            String id = String.valueOf(emailDispatchIdCounter.getAndIncrement());
            entity.setId(id);
            entity.setTechnicalId(UUID.randomUUID());

            emailDispatchCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);

            processEmailDispatch(entity);
            return id;
        }

        private EmailDispatch getEmailDispatchById(String id) {
            List<EmailDispatch> list = emailDispatchCache.get("entities");
            if (list == null) return null;
            synchronized (list) {
                return list.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
            }
        }

        private boolean updateEmailDispatchById(String id, EmailDispatch updated) {
            List<EmailDispatch> list = emailDispatchCache.get("entities");
            if (list == null) return false;
            synchronized (list) {
                for (int i = 0; i < list.size(); i++) {
                    EmailDispatch current = list.get(i);
                    if (id.equals(current.getId())) {
                        updated.setId(id);
                        updated.setTechnicalId(current.getTechnicalId());
                        list.set(i, updated);
                        processEmailDispatch(updated);
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean deleteEmailDispatchById(String id) {
            List<EmailDispatch> list = emailDispatchCache.get("entities");
            if (list == null) return false;
            synchronized (list) {
                return list.removeIf(e -> id.equals(e.getId()));
            }
        }

        private void processEmailDispatch(EmailDispatch entity) {
            logger.info("Simulating processing of EmailDispatch id={} with status={}", entity.getId(), entity.getStatus());
            // TODO: Add real event processing logic here or call Cyoda workflow trigger
        }
    }

    // ====== DIGEST DATA CRUD (keep local cache as minor entity) ======

    @RestController
    @RequestMapping(path = "/prototype/digestData")
    public static class DigestDataController {

        private static final Logger logger = LoggerFactory.getLogger(DigestDataController.class);

        private final Map<String, List<DigestData>> digestDataCache = new HashMap<>();
        private final AtomicLong digestDataIdCounter = new AtomicLong(1);

        @Data
        public static class DigestDataDto {
            @NotBlank
            private String dataPayload;
        }

        @PostMapping
        public ResponseEntity<Map<String, Object>> createDigestData(@RequestBody @Valid DigestDataDto dto) {
            logger.info("Received request to create DigestData: {}", dto);

            DigestData entity = new DigestData();
            entity.setDataPayload(dto.getDataPayload());

            if (!entity.isValid()) {
                logger.error("Invalid DigestData data");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestData data");
            }

            String id = addDigestData(entity);
            logger.info("DigestData created with id {}", id);

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "created");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping
        public ResponseEntity<List<DigestData>> listDigestData() {
            logger.info("Fetching all DigestData");
            List<DigestData> list = digestDataCache.getOrDefault("entities", Collections.emptyList());
            return ResponseEntity.ok(Collections.unmodifiableList(list));
        }

        @GetMapping("/{id}")
        public ResponseEntity<DigestData> getDigestData(@PathVariable @NotBlank String id) {
            logger.info("Fetching DigestData with id {}", id);
            DigestData dd = getDigestDataById(id);
            if (dd == null) {
                logger.error("DigestData with id {} not found", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
            }
            return ResponseEntity.ok(dd);
        }

        @PutMapping("/{id}")
        public ResponseEntity<Map<String, Object>> updateDigestData(@PathVariable @NotBlank String id,
                                                                    @RequestBody @Valid DigestDataDto dto) {
            logger.info("Updating DigestData with id {}", id);

            DigestData entity = new DigestData();
            entity.setDataPayload(dto.getDataPayload());

            if (!entity.isValid()) {
                logger.error("Invalid DigestData data");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestData data");
            }

            boolean updated = updateDigestDataById(id, entity);
            if (!updated) {
                logger.error("DigestData with id {} not found for update", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "updated");
            return ResponseEntity.ok(response);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Map<String, Object>> deleteDigestData(@PathVariable @NotBlank String id) {
            logger.info("Deleting DigestData with id {}", id);
            boolean deleted = deleteDigestDataById(id);
            if (!deleted) {
                logger.error("DigestData with id {} not found for delete", id);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
            }
            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "deleted");
            return ResponseEntity.ok(response);
        }

        private String addDigestData(DigestData entity) {
            String id = String.valueOf(digestDataIdCounter.getAndIncrement());
            entity.setId(id);
            entity.setTechnicalId(UUID.randomUUID());

            digestDataCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);

            processDigestData(entity);
            return id;
        }

        private DigestData getDigestDataById(String id) {
            List<DigestData> list = digestDataCache.get("entities");
            if (list == null) return null;
            synchronized (list) {
                return list.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
            }
        }

        private boolean updateDigestDataById(String id, DigestData updated) {
            List<DigestData> list = digestDataCache.get("entities");
            if (list == null) return false;
            synchronized (list) {
                for (int i = 0; i < list.size(); i++) {
                    DigestData current = list.get(i);
                    if (id.equals(current.getId())) {
                        updated.setId(id);
                        updated.setTechnicalId(current.getTechnicalId());
                        list.set(i, updated);
                        processDigestData(updated);
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean deleteDigestDataById(String id) {
            List<DigestData> list = digestDataCache.get("entities");
            if (list == null) return false;
            synchronized (list) {
                return list.removeIf(e -> id.equals(e.getId()));
            }
        }

        private void processDigestData(DigestData entity) {
            logger.info("Simulating processing of DigestData id={} with dataPayload={}", entity.getId(), entity.getDataPayload());
            // TODO: Add real event processing logic here or call Cyoda workflow trigger
        }
    }

    // Utility class for converting ObjectNode to entity
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convertObjectNodeToObject(ObjectNode node, Class<T> clazz) {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException("Failed to convert ObjectNode to " + clazz.getSimpleName(), e);
            }
        }
    }
}