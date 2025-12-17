package com.java_template.application.controller;

import com.java_template.application.entity.kycprofile.version_1.KYCProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * KYCProfileController - REST API for KYC Profile entity management
 */
@RestController
@RequestMapping("/ui/kyc")
@CrossOrigin(origins = "*")
public class KYCProfileController {

    private static final Logger logger = LoggerFactory.getLogger(KYCProfileController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public KYCProfileController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<KYCProfile>> createKYCProfile(@Valid @RequestBody KYCProfile profile) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(KYCProfile.ENTITY_NAME).withVersion(KYCProfile.ENTITY_VERSION);
            EntityWithMetadata<KYCProfile> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, profile.getKycProfileId(), "kycProfileId", KYCProfile.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<KYCProfile> response = entityService.create(profile);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create KYC profile", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<KYCProfile>> getKYCProfileById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(KYCProfile.ENTITY_NAME).withVersion(KYCProfile.ENTITY_VERSION);
            EntityWithMetadata<KYCProfile> response = entityService.getById(id, modelSpec, KYCProfile.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve KYC profile", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<EntityWithMetadata<KYCProfile>> getKYCProfileByUserId(@PathVariable String userId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(KYCProfile.ENTITY_NAME).withVersion(KYCProfile.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.userId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(userId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            PageResult<EntityWithMetadata<KYCProfile>> result = entityService.search(
                    modelSpec, groupCondition, KYCProfile.class,
                    SearchAndRetrievalParams.builder().pageSize(1).pageNumber(0).build());

            return !result.data().isEmpty() ? ResponseEntity.ok(result.data().get(0)) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve KYC profile", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PageResult<EntityWithMetadata<KYCProfile>>> getKYCProfilesByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(KYCProfile.ENTITY_NAME).withVersion(KYCProfile.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<KYCProfile>> result = entityService.search(
                    modelSpec, groupCondition, KYCProfile.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve KYC profiles", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<KYCProfile>> updateKYCProfile(
            @PathVariable UUID id,
            @Valid @RequestBody KYCProfile profile,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<KYCProfile> response = entityService.update(id, profile, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update KYC profile", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKYCProfile(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete KYC profile", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

