package com.example.application.controller;

import com.example.application.entity.product_performance_report.version_1.ProductPerformanceReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ProductPerformanceReportController
 * REST controller for ProductPerformanceReport CRUD operations
 */
@RestController
@RequestMapping("/ui/product-performance-report")
@CrossOrigin(origins = "*")
public class ProductPerformanceReportController {

    private static final Logger logger = LoggerFactory.getLogger(ProductPerformanceReportController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProductPerformanceReportController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> createReport(
            @RequestBody ProductPerformanceReport report) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec()
                    .withName(ProductPerformanceReport.ENTITY_NAME)
                    .withVersion(ProductPerformanceReport.ENTITY_VERSION);

            EntityWithMetadata<ProductPerformanceReport> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, report.getReportId(), "reportId", ProductPerformanceReport.class);

            if (existing != null) {
                logger.warn("Report with ID {} already exists", report.getReportId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        String.format("Report already exists with ID: %s", report.getReportId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Set timestamps
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<ProductPerformanceReport> response = entityService.create(report);
            logger.info("Report created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(response.metadata().getId())
                    .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to create report: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> getReportById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(ProductPerformanceReport.ENTITY_NAME)
                    .withVersion(ProductPerformanceReport.ENTITY_VERSION);

            EntityWithMetadata<ProductPerformanceReport> response = entityService.getById(
                    id, modelSpec, ProductPerformanceReport.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve report: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/business/{reportId}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> getReportByBusinessId(
            @PathVariable String reportId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(ProductPerformanceReport.ENTITY_NAME)
                    .withVersion(ProductPerformanceReport.ENTITY_VERSION);

            EntityWithMetadata<ProductPerformanceReport> response = entityService.findByBusinessId(
                    modelSpec, reportId, "reportId", ProductPerformanceReport.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to retrieve report: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ProductPerformanceReport>> updateReport(
            @PathVariable UUID id,
            @RequestBody ProductPerformanceReport report,
            @RequestParam(required = false) String transition) {
        try {
            report.setUpdatedAt(LocalDateTime.now());
            EntityWithMetadata<ProductPerformanceReport> response = entityService.update(id, report, transition);
            logger.info("Report updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to update report: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<PageResult<EntityWithMetadata<ProductPerformanceReport>>> searchReports(
            @RequestParam(required = false) String reportWeek,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(ProductPerformanceReport.ENTITY_NAME)
                    .withVersion(ProductPerformanceReport.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (reportWeek != null && !reportWeek.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.reportWeek")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(reportWeek)));
            }

            PageResult<EntityWithMetadata<ProductPerformanceReport>> pageResult;
            if (conditions.isEmpty()) {
                pageResult = entityService.findAll(
                        modelSpec, ProductPerformanceReport.class,
                        SearchAndRetrievalParams.builder()
                                .pageSize(size)
                                .pageNumber(page)
                                .build());
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                pageResult = entityService.search(
                        modelSpec, condition, ProductPerformanceReport.class,
                        SearchAndRetrievalParams.builder()
                                .pageSize(size)
                                .pageNumber(page)
                                .build());
            }

            logger.info("Search returned {} reports", pageResult.data().size());
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to search reports: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Report deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to delete report: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

