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
            @RequestBody ProductPerformanceReport entity) {
        try {
            EntityWithMetadata<ProductPerformanceReport> created = entityService.create(entity);
            return ResponseEntity.created(URI.create("/api/v1/product-performance-reports/" + created.metadata().getId()))
                    .body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get ProductPerformanceReport by UUID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> getById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(ProductPerformanceReport.ENTITY_NAME)
                    .withVersion(ProductPerformanceReport.ENTITY_VERSION);

            EntityWithMetadata<ProductPerformanceReport> report =
                    entityService.getById(id, modelSpec, ProductPerformanceReport.class);

            if (report == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Update ProductPerformanceReport
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> update(
            @PathVariable UUID id,
            @RequestBody ProductPerformanceReport entity) {
        try {
            EntityWithMetadata<ProductPerformanceReport> updated = entityService.update(id, entity, null);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Delete ProductPerformanceReport
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Search ProductPerformanceReports with pagination
     */
    @GetMapping
    public ResponseEntity<Object> search(
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "0") int pageNumber) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(ProductPerformanceReport.ENTITY_NAME)
                    .withVersion(ProductPerformanceReport.ENTITY_VERSION);

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(pageSize)
                    .pageNumber(pageNumber)
                    .build();

            var results = entityService.findAll(modelSpec, ProductPerformanceReport.class, params);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}

