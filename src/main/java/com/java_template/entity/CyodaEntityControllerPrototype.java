```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Slf4j
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/analyzeComments")
    public CompletableFuture<ResponseEntity<String>> analyzeComments(@RequestBody @Valid CommentRequest request) {
        long postId = request.getPostId();
        String email = request.getEmail();

        String url = "https://jsonplaceholder.typicode.com/comments?postId=" + postId;
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = new RestTemplate().getForObject(url, String.class);
                JsonNode comments = objectMapper.readTree(response);

                // Implement real analysis logic here
                // Placeholder for analysis
                CompletableFuture<UUID> analysisResultFuture = entityService.addItem(
                        "AnalysisResult",
                        ENTITY_VERSION,
                        new AnalysisResult(postId, 0.75, new String[]{"example"}, true)
                );

                logger.info("Sending analysis report to email: {}", email);

                return ResponseEntity.ok("Analysis complete and report sent to email.");
            } catch (Exception e) {
                logger.error("Error during analysis: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        });
    }

    @GetMapping("/getAnalysisResults")
    public CompletableFuture<ResponseEntity<Map<UUID, AnalysisResult>>> getAnalysisResults() {
        return entityService.getItems("AnalysisResult", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    Map<UUID, AnalysisResult> results = new ConcurrentHashMap<>();
                    arrayNode.forEach(jsonNode -> {
                        try {
                            AnalysisResult result = objectMapper.treeToValue(jsonNode, AnalysisResult.class);
                            UUID technicalId = UUID.fromString(jsonNode.get("technicalId").asText());
                            results.put(technicalId, result);
                        } catch (Exception e) {
                            logger.error("Error converting analysis result: {}", e.getMessage());
                        }
                    });
                    return ResponseEntity.ok(results);
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body("Error: " + ex.getStatusCode().toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CommentRequest {
        @Min(1)
        private long postId;

        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class AnalysisResult {
        private long postId;
        private double sentimentScore;
        private String[] keyPhrases;
        private boolean emailSent;
    }
}
```