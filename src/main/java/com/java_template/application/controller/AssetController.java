package com.java_template.application.controller;

import com.java_template.application.entity.asset.version_1.Asset;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * AssetController - REST API for Asset entity management
 */
@RestController
@RequestMapping("/ui/asset")
@CrossOrigin(origins = "*")
public class AssetController {

    private static final Logger logger = LoggerFactory.getLogger(AssetController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AssetController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Asset>> createAsset(@Valid @RequestBody Asset asset) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Asset.ENTITY_NAME).withVersion(Asset.ENTITY_VERSION);
            EntityWithMetadata<Asset> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, asset.getAssetId(), "assetId", Asset.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<Asset> response = entityService.create(asset);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create asset", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Asset>> getAssetById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Asset.ENTITY_NAME).withVersion(Asset.ENTITY_VERSION);
            EntityWithMetadata<Asset> response = entityService.getById(id, modelSpec, Asset.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve asset", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<EntityWithMetadata<Asset>> getAssetBySymbol(@PathVariable String symbol) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Asset.ENTITY_NAME).withVersion(Asset.ENTITY_VERSION);
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.symbol")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(symbol));
            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));
            
            PageResult<EntityWithMetadata<Asset>> result = entityService.search(
                    modelSpec, groupCondition, Asset.class,
                    SearchAndRetrievalParams.builder().pageSize(1).pageNumber(0).build());
            
            return !result.data().isEmpty() ? ResponseEntity.ok(result.data().get(0)) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve asset by symbol", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Asset>> updateAsset(
            @PathVariable UUID id,
            @Valid @RequestBody Asset asset,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Asset> response = entityService.update(id, asset, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update asset", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete asset", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

