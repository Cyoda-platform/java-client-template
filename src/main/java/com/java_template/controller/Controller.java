package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    // AtomicInteger for thread-safe counting
    private final AtomicInteger totalEmailsSent = new AtomicInteger(0);

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // DTOs

    public static class SignupRequest {
        @NotBlank
        @Email
        private String email;
        @Size(max = 100)
        private String name;

        public SignupRequest() {
        }

        public SignupRequest(String email, String name) {
            this.email = email;
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class SignupResponse {
        private String userId;
        private String message;

        public SignupResponse() {
        }

        public SignupResponse(String userId, String message) {
            this.userId = userId;
            this.message = message;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class WeeklyFactSendResponse {
        private String factId;
        private String factText;
        private int recipientsCount;
        private String message;

        public WeeklyFactSendResponse() {
        }

        public WeeklyFactSendResponse(String factId, String factText, int recipientsCount, String message) {
            this.factId = factId;
            this.factText = factText;
            this.recipientsCount = recipientsCount;
            this.message = message;
        }

        public String getFactId() {
            return factId;
        }

        public void setFactId(String factId) {
            this.factId = factId;
        }

        public String getFactText() {
            return factText;
        }

        public void setFactText(String factText) {
            this.factText = factText;
        }

        public int getRecipientsCount() {
            return recipientsCount;
        }

        public void setRecipientsCount(int recipientsCount) {
            this.recipientsCount = recipientsCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class Subscriber {
        private String userId;
        private String email;
        private String name;
        private Instant subscribedAt;

        public Subscriber() {
        }

        public Subscriber(String userId, String email, String name, Instant subscribedAt) {
            this.userId = userId;
            this.email = email;
            this.name = name;
            this.subscribedAt = subscribedAt;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Instant getSubscribedAt() {
            return subscribedAt;
        }

        public void setSubscribedAt(Instant subscribedAt) {
            this.subscribedAt = subscribedAt;
        }
    }

    public static class CatFact {
        private String factId;
        private String factText;
        private Instant sentAt;
        private int recipientsCount;

        public CatFact() {
        }

        public CatFact(String factId, String factText, Instant sentAt, int recipientsCount) {
            this.factId = factId;
            this.factText = factText;
            this.sentAt = sentAt;
            this.recipientsCount = recipientsCount;
        }

        public String getFactId() {
            return factId;
        }

        public void setFactId(String factId) {
            this.factId = factId;
        }

        public String getFactText() {
            return factText;
        }

        public void setFactText(String factText) {
            this.factText = factText;
        }

        public Instant getSentAt() {
            return sentAt;
        }

        public void setSentAt(Instant sentAt) {
            this.sentAt = sentAt;
        }

        public int getRecipientsCount() {
            return recipientsCount;
        }

        public void setRecipientsCount(int recipientsCount) {
            this.recipientsCount = recipientsCount;
        }
    }

    public static class ReportSummary {
        private int totalSubscribers;
        private int totalEmailsSent;
        private Instant lastFactSentAt;

        public ReportSummary() {
        }

        public ReportSummary(int totalSubscribers, int totalEmailsSent, Instant lastFactSentAt) {
            this.totalSubscribers = totalSubscribers;
            this.totalEmailsSent = totalEmailsSent;
            this.lastFactSentAt = lastFactSentAt;
        }

        public int getTotalSubscribers() {
            return totalSubscribers;
        }

        public void setTotalSubscribers(int totalSubscribers) {
            this.totalSubscribers = totalSubscribers;
        }

        public int getTotalEmailsSent() {
            return totalEmailsSent;
        }

        public void setTotalEmailsSent(int totalEmailsSent) {
            this.totalEmailsSent = totalEmailsSent;
        }

        public Instant getLastFactSentAt() {
            return lastFactSentAt;
        }

        public void setLastFactSentAt(Instant lastFactSentAt) {
            this.lastFactSentAt = lastFactSentAt;
        }
    }

    private static final String ENTITY_MODEL_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_MODEL_CATFACT = "CatFact";

    @PostMapping(value = "/users/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signupUser(@Valid @RequestBody SignupRequest request) {
        logger.info("Signup request received for email={}", request.getEmail());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", request.getEmail()));

        CompletableFuture<ArrayNode> existingSubsFuture =
                entityService.getItemsByCondition(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION, condition);

        ArrayNode existingSubs = existingSubsFuture.join();

        if (existingSubs != null && existingSubs.size() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }

        String userId = UUID.randomUUID().toString();

        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("userId", userId);
        subscriberNode.put("email", request.getEmail());
        subscriberNode.put("name", request.getName());

        CompletableFuture<UUID> addFuture = entityService.addItem(
                ENTITY_MODEL_SUBSCRIBER,
                ENTITY_VERSION,
                subscriberNode
        );
        addFuture.join();

        logger.info("User subscribed: userId={}, email={}", userId, request.getEmail());
        return ResponseEntity.created(URI.create("/cyoda/api/users/" + userId))
                .body(new SignupResponse(userId, "Subscription successful."));
    }

    @PostMapping(value = "/facts/sendWeekly", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeeklyFactSendResponse> sendWeeklyCatFact() {
        logger.info("Triggering weekly cat fact creation");

        ObjectNode catFactNode = objectMapper.createObjectNode();

        CompletableFuture<UUID> addCatFactFuture = entityService.addItem(
                ENTITY_MODEL_CATFACT,
                ENTITY_VERSION,
                catFactNode
        );

        UUID catFactId = addCatFactFuture.join();

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.factId", "EQUALS", catFactId.toString()));

        CompletableFuture<ArrayNode> factsFuture = entityService.getItemsByCondition(
                ENTITY_MODEL_CATFACT,
                ENTITY_VERSION,
                condition
        );

        ArrayNode factsArray = factsFuture.join();

        if (factsArray == null || factsArray.size() == 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve stored cat fact");
        }

        ObjectNode storedFact = (ObjectNode) factsArray.get(0);
        String factText = storedFact.path("factText").asText(null);
        int recipientsCount = storedFact.path("recipientsCount").asInt(0);

        logger.info("Weekly cat fact created and emails triggered: factId={}, recipientsCount={}", catFactId, recipientsCount);
        return ResponseEntity.ok(new WeeklyFactSendResponse(catFactId.toString(), factText, recipientsCount, "Weekly cat fact sent successfully."));
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        logger.info("Retrieving subscribers");
        CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subsArray = subsFuture.join();

        List<Subscriber> subscribers = new ArrayList<>();
        if (subsArray != null) {
            subsArray.forEach(node -> {
                try {
                    Subscriber s = objectMapper.treeToValue(node, Subscriber.class);
                    subscribers.add(s);
                } catch (Exception e) {
                    logger.error("Failed to parse subscriber entity", e);
                }
            });
        }
        logger.info("Retrieved subscribers count={}", subscribers.size());
        return ResponseEntity.ok(subscribers);
    }

    @GetMapping(value = "/facts/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CatFact>> getFactsHistory() {
        logger.info("Retrieving fact history");
        CompletableFuture<ArrayNode> factsFuture = entityService.getItems(ENTITY_MODEL_CATFACT, ENTITY_VERSION);
        ArrayNode factsArray = factsFuture.join();

        List<CatFact> catFacts = new ArrayList<>();
        if (factsArray != null) {
            factsArray.forEach(node -> {
                try {
                    CatFact cf = objectMapper.treeToValue(node, CatFact.class);
                    catFacts.add(cf);
                } catch (Exception e) {
                    logger.error("Failed to parse cat fact entity", e);
                }
            });
        }

        catFacts.sort(Comparator.comparing(CatFact::getSentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        logger.info("Retrieved fact history count={}", catFacts.size());
        return ResponseEntity.ok(catFacts);
    }

    @GetMapping(value = "/report/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportSummary> getReportSummary() {
        logger.info("Generating report summary");
        CompletableFuture<ArrayNode> factsFuture = entityService.getItems(ENTITY_MODEL_CATFACT, ENTITY_VERSION);
        ArrayNode factsArray = factsFuture.join();

        Instant lastSentAt = null;
        if (factsArray != null && factsArray.size() > 0) {
            List<CatFact> catFacts = new ArrayList<>();
            factsArray.forEach(node -> {
                try {
                    CatFact cf = objectMapper.treeToValue(node, CatFact.class);
                    catFacts.add(cf);
                } catch (Exception e) {
                    logger.error("Failed to parse cat fact entity", e);
                }
            });
            lastSentAt = catFacts.stream()
                    .map(CatFact::getSentAt)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null);
        }

        CompletableFuture<ArrayNode> subsFuture = entityService.getItems(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subsArray = subsFuture.join();
        int totalSubscribers = subsArray != null ? subsArray.size() : 0;

        return ResponseEntity.ok(new ReportSummary(totalSubscribers, totalEmailsSent.get(), lastSentAt));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason(), ex);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        errorBody.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }
}