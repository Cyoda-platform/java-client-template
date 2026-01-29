package com.java_template.application.controller;

import com.java_template.application.entity.product_performance_report.version_1.ProductPerformanceReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.service.SearchAndRetrievalParams;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

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
        
        ProductPerformanceReport report = request.entity();
        
        // Check for duplicate business ID
        List<EntityWithMetadata<ProductPerformanceReport>> existing = entityService.search(
                ProductPerformanceReport.class,
                new SearchAndRetrievalParams()
        );
        
        if (existing.stream().anyMatch(e -> e.entity().getReportId().equals(report.getReportId()))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        EntityWithMetadata<ProductPerformanceReport> created = entityService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/product-performance-reports/" + created.metadata().getUuid()))
                .body(created);
    }

    /**
     * Get ProductPerformanceReport by UUID
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> getByUuid(@PathVariable String uuid) {
        Optional<EntityWithMetadata<ProductPerformanceReport>> report = 
                entityService.getByUuid(uuid, ProductPerformanceReport.class);
        return report.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get ProductPerformanceReport by business ID (reportId)
     */
    @GetMapping("/by-report-id/{reportId}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> getByReportId(@PathVariable String reportId) {
        List<EntityWithMetadata<ProductPerformanceReport>> results = entityService.search(
                ProductPerformanceReport.class,
                new SearchAndRetrievalParams()
        );
        
        Optional<EntityWithMetadata<ProductPerformanceReport>> report = results.stream()
                .filter(r -> r.entity().getReportId().equals(reportId))
                .findFirst();
        
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
        
        EntityWithMetadata<ProductPerformanceReport> updated = entityService.update(uuid, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete ProductPerformanceReport
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        entityService.delete(uuid, ProductPerformanceReport.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search ProductPerformanceReports with pagination
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<ProductPerformanceReport>>> search(
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "0") int pageNumber) {
        
        SearchAndRetrievalParams params = new SearchAndRetrievalParams();
        params.setPageSize(pageSize);
        params.setPageNumber(pageNumber);
        
        List<EntityWithMetadata<ProductPerformanceReport>> results = 
                entityService.search(ProductPerformanceReport.class, params);
        
        return ResponseEntity.ok(results);
    }
}

