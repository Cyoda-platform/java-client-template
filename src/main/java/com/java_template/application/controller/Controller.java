package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;

import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.SearchConditionRequest;
import com.java_template.common.workflow.Condition;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - create Job
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Job job) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                job
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createJob", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /jobs/{technicalId} - get Job by technicalId
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJobById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode jobNode = itemFuture.get();
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJobById", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getJobById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getJobById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /laureates/{technicalId} - get Laureate by technicalId
    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<?> getLaureateById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode laureateNode = itemFuture.get();
            return ResponseEntity.ok(laureateNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getLaureateById", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // POST /subscribers - create Subscriber
    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                subscriber
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createSubscriber", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /subscribers/{technicalId} - get Subscriber by technicalId
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode subscriberNode = itemFuture.get();
            return ResponseEntity.ok(subscriberNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getSubscriberById", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriberById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class TechnicalIdResponse {
        private final String technicalId;
    }
}