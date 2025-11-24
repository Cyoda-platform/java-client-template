package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for managing email campaigns.
 * Provides CRUD operations and search functionality for email campaign entities.
 */
@RestController
@RequestMapping("/ui/emailcampaign")
@CrossOrigin(origins = "*")
public class EmailCampaignController {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> createEmailCampaign(@Valid @RequestBody EmailCampaign campaign) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            EntityWithMetadata<EmailCampaign> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, campaign.getCampaignId(), "campaignId", EmailCampaign.class);

            if (existing != null) {
                logger.warn("EmailCampaign with ID {} already exists", campaign.getCampaignId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("EmailCampaign already exists with ID: %s", campaign.getCampaignId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<EmailCampaign> response = entityService.create(campaign);
            logger.info("EmailCampaign created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create email campaign: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> getEmailCampaignById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<EmailCampaign> response = entityService.getById(id, modelSpec, EmailCampaign.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve email campaign with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/business/{campaignId}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> getEmailCampaignByBusinessId(
            @PathVariable String campaignId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<EmailCampaign> response = entityService.findByBusinessId(
                    modelSpec, campaignId, "campaignId", EmailCampaign.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve email campaign with business ID '%s': %s", campaignId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailCampaign>> updateEmailCampaign(
            @PathVariable UUID id,
            @Valid @RequestBody EmailCampaign campaign,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<EmailCampaign> response = entityService.update(id, campaign, transition);
            logger.info("EmailCampaign updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update email campaign with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<EmailCampaign>>> listEmailCampaigns(
            Pageable pageable,
            @RequestParam(required = false) Integer weekNumber,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailCampaign.ENTITY_NAME).withVersion(EmailCampaign.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (weekNumber != null) {
                SimpleCondition weekCondition = new SimpleCondition()
                        .withJsonPath("$.weekNumber")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(weekNumber));
                conditions.add(weekCondition);
            }

            if (conditions.isEmpty()) {
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, EmailCampaign.class, pointInTimeDate));
            } else {
                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                List<EntityWithMetadata<EmailCampaign>> entities = entityService.search(modelSpec, groupCondition, EmailCampaign.class, pointInTimeDate);

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), entities.size());
                List<EntityWithMetadata<EmailCampaign>> pageContent = start < entities.size()
                    ? entities.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<EmailCampaign>> page = new PageImpl<>(pageContent, pageable, entities.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list email campaigns: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailCampaign(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("EmailCampaign deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete email campaign with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

