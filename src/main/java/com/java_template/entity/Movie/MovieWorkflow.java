package com.java_template.entity.Movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class MovieWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(MovieWorkflow.class);

    private final ObjectMapper objectMapper;

    public MovieWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processMovie(ObjectNode movieNode) {
        logger.info("Processing Movie entity in workflow before persistence: {}", movieNode.path("name").asText());

        if (movieNode.has("processedByWorkflow") && movieNode.get("processedByWorkflow").asBoolean(false)) {
            logger.warn("Movie entity already processed by workflow, skipping further processing");
            return CompletableFuture.completedFuture(movieNode);
        }
        movieNode.put("processedByWorkflow", true);

        CompletableFuture<ObjectNode> future = CompletableFuture.completedFuture(movieNode)
                .thenCompose(this::processSetDefaultGenre)
                .thenCompose(this::processValidateYear)
                .thenCompose(this::processValidateNominations)
                .thenCompose(this::processAsyncWorkflow);

        return future;
    }

    private CompletableFuture<ObjectNode> processSetDefaultGenre(ObjectNode movieNode) {
        if (!movieNode.hasNonNull("genre") || movieNode.get("genre").asText().trim().isEmpty()) {
            movieNode.put("genre", "Unknown");
            logger.info("Set default genre 'Unknown' for Movie {}", movieNode.path("name").asText());
        }
        return CompletableFuture.completedFuture(movieNode);
    }

    private CompletableFuture<ObjectNode> processValidateYear(ObjectNode movieNode) {
        if (movieNode.hasNonNull("year")) {
            int year = movieNode.get("year").asInt(-1);
            if (year < 1888 || year > 2100) {
                logger.warn("Provided year {} for movie {} is out of accepted range (1888-2100), setting to 1900",
                        year, movieNode.path("name").asText());
                movieNode.put("year", 1900);
            }
        } else {
            movieNode.put("year", 1900);
            logger.info("Year not specified for movie {}, set to default 1900", movieNode.path("name").asText());
        }
        return CompletableFuture.completedFuture(movieNode);
    }

    private CompletableFuture<ObjectNode> processValidateNominations(ObjectNode movieNode) {
        if (!movieNode.hasNonNull("nominations") || !movieNode.get("nominations").isArray() || movieNode.get("nominations").size() == 0) {
            logger.warn("Movie {} nominations missing or empty, setting empty array", movieNode.path("name").asText());
            ArrayNode nominationsArray = objectMapper.createArrayNode();
            movieNode.set("nominations", nominationsArray);
        }
        return CompletableFuture.completedFuture(movieNode);
    }

    private CompletableFuture<ObjectNode> processAsyncWorkflow(ObjectNode movieNode) {
        CompletableFuture.runAsync(() -> {
            UUID techId = null;
            if (movieNode.hasNonNull("technicalId")) {
                try {
                    techId = UUID.fromString(movieNode.get("technicalId").asText());
                } catch (Exception ignored) {
                }
            }
            logger.info("Async workflow processing for Movie technicalId {} started", techId);

            // TODO: implement event-driven workflow or other async tasks here
            try {
                Thread.sleep(1000); // simulate delay
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            logger.info("Async workflow processing for Movie technicalId {} completed", techId);
        });
        return CompletableFuture.completedFuture(movieNode);
    }
}