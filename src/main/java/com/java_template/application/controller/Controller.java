import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

package com.java_template.application.controller;

@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - Create a new ingestion Job
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody ObjectNode jobData) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "Job",
                    "1",
                    jobData
            );
            UUID id = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse(id.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in createJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /jobs/{technicalId} - Retrieve Job details by technicalId
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "Job",
                    "1",
                    uuid
            );
            ObjectNode job = itemFuture.get();
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // POST /subscribers - Register a new Subscriber
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody ObjectNode subscriberData) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "Subscriber",
                    "1",
                    subscriberData
            );
            UUID id = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse(id.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /subscribers/{technicalId} - Retrieve Subscriber details by technicalId
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "Subscriber",
                    "1",
                    uuid
            );
            ObjectNode subscriber = itemFuture.get();
            return ResponseEntity.ok(subscriber);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /subscribers - Retrieve all Subscribers (optional)
    @GetMapping("/subscribers")
    public ResponseEntity<?> getAllSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    "Subscriber",
                    "1"
            );
            ArrayNode subscribers = itemsFuture.get();
            return ResponseEntity.ok(subscribers);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getAllSubscribers", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getAllSubscribers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in getAllSubscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /laureates/{technicalId} - Retrieve Laureate details by technicalId
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureate(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "Laureate",
                    "1",
                    uuid
            );
            ObjectNode laureate = itemFuture.get();
            return ResponseEntity.ok(laureate);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // GET /laureates - Retrieve Laureates by conditions (optional, e.g., year, category)
    @GetMapping("/laureates")
    public ResponseEntity<?> getLaureatesByCondition(@RequestParam(required = false) String year,
                                                    @RequestParam(required = false) String category) {
        try {
            if (year == null && category == null) {
                // No conditions, return all laureates
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        "Laureate",
                        "1"
                );
                ArrayNode laureates = itemsFuture.get();
                return ResponseEntity.ok(laureates);
            } else {
                // Build condition group AND
                java.util.List<com.java_template.common.model.Condition> conditions = new java.util.ArrayList<>();
                if (year != null) {
                    conditions.add(com.java_template.common.model.Condition.of("$.year", "EQUALS", year));
                }
                if (category != null) {
                    conditions.add(com.java_template.common.model.Condition.of("$.category", "EQUALS", category));
                }
                com.java_template.common.model.SearchConditionRequest conditionGroup =
                        com.java_template.common.model.SearchConditionRequest.group("AND", conditions);

                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                        "Laureate",
                        "1",
                        conditionGroup,
                        true
                );
                ArrayNode filteredLaureates = filteredItemsFuture.get();
                return ResponseEntity.ok(filteredLaureates);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureatesByCondition", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception in getLaureatesByCondition", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureatesByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Data
    public static class TechnicalIdResponse {
        private final String technicalId;
    }
}