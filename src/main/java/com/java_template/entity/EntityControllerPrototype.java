import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, String> subscribers = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostMapping("/users/signup") // must be first
    public ResponseEntity<String> signUp(@RequestBody @Valid EmailRequest emailRequest) {
        subscribers.put(emailRequest.getEmail(), "subscribed");
        logger.info("User signed up with email: {}", emailRequest.getEmail());
        return ResponseEntity.status(201).body("{\"message\": \"User signed up successfully.\"}");
    }

    @PostMapping("/cat-facts/retrieve") // must be first
    public ResponseEntity<String> retrieveCatFact() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(CAT_FACT_API_URL, String.class);
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String fact = jsonResponse.get("fact").asText();
            logger.info("Retrieved cat fact: {}", fact);
            return ResponseEntity.ok("{\"fact\": \"" + fact + "\"}");
        } catch (Exception e) {
            logger.error("Error retrieving cat fact", e);
            throw new ResponseStatusException(response.getStatusCode(), e.getMessage());
        }
    }

    @PostMapping("/cat-facts/send") // must be first
    public ResponseEntity<String> sendWeeklyCatFact() {
        CompletableFuture.runAsync(() -> {
            // TODO: Implement actual email sending logic
            logger.info("Sending weekly cat fact to all subscribers...");
            subscribers.forEach((email, status) -> {
                logger.info("Sent cat fact to: {}", email);
            });
        });
        return ResponseEntity.ok("{\"message\": \"Weekly cat fact sent to all subscribers.\"}");
    }

    @PostMapping("/users/unsubscribe") // must be first
    public ResponseEntity<String> unsubscribe(@RequestBody @Valid EmailRequest emailRequest) {
        subscribers.remove(emailRequest.getEmail());
        logger.info("User unsubscribed with email: {}", emailRequest.getEmail());
        return ResponseEntity.ok("{\"message\": \"User unsubscribed successfully.\"}");
    }

    @GetMapping("/report/subscriber-count") // must be first
    public ResponseEntity<String> getSubscriberCount() {
        int count = subscribers.size();
        logger.info("Number of subscribers: {}", count);
        return ResponseEntity.ok("{\"count\": " + count + "}");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body("{\"error\": \"" + ex.getStatusCode().toString() + "\"}");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmailRequest {
        @NotNull
        @NotBlank
        @Email
        private String email;
    }
}