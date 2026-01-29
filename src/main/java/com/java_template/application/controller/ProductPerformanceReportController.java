package com.java_template.application.controller;

import com.java_template.application.entity.product_performance_report.version_1.ProductPerformanceReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * ProductPerformanceReportController
 * REST API endpoints for ProductPerformanceReport CRUD operations
 */
@RestController
@RequestMapping("/api/v1/product-performance-reports")
public class ProductPerformanceReportController {

    private final EntityService entityService;

    public ProductPerformanceReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Create a new ProductPerformanceReport
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> create(
            @RequestBody EntityWithMetadata<ProductPerformanceReport> request) {

        EntityWithMetadata<ProductPerformanceReport> created = entityService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/product-performance-reports/" + created.metadata().getUuid()))
                .body(created);
    }

    /**
     * Get ProductPerformanceReport by UUID
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> getByUuid(@PathVariable String uuid) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(ProductPerformanceReport.ENTITY_NAME)
                .withVersion(ProductPerformanceReport.ENTITY_VERSION);

        Optional<EntityWithMetadata<ProductPerformanceReport>> report =
                entityService.getByUuid(UUID.fromString(uuid), modelSpec, ProductPerformanceReport.class);
        return report.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Update ProductPerformanceReport
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> update(
            @PathVariable String uuid,
            @RequestBody EntityWithMetadata<ProductPerformanceReport> request) {

        EntityWithMetadata<ProductPerformanceReport> updated = entityService.update(UUID.fromString(uuid), request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete ProductPerformanceReport
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        ModelSpec modelSpec = new ModelSpec()
                .withName(ProductPerformanceReport.ENTITY_NAME)
                .withVersion(ProductPerformanceReport.ENTITY_VERSION);

        entityService.delete(UUID.fromString(uuid), modelSpec, ProductPerformanceReport.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search ProductPerformanceReports with pagination
     */
    @GetMapping
    public ResponseEntity<Object> search(
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "0") int pageNumber) {

        ModelSpec modelSpec = new ModelSpec()
                .withName(ProductPerformanceReport.ENTITY_NAME)
                .withVersion(ProductPerformanceReport.ENTITY_VERSION);

        SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                .pageSize(pageSize)
                .pageNumber(pageNumber)
                .build();

        var results = entityService.findAll(modelSpec, ProductPerformanceReport.class, params);
        return ResponseEntity.ok(results);
    }
}

