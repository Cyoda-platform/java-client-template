package com.java_template.application.controller;

import com.java_template.application.entity.performancemetric.version_1.PerformanceMetric;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/performance-metrics")
public class PerformanceMetricController {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMetricController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<EntityResponse<PerformanceMetric>> createMetric(@RequestBody PerformanceMetric metric) {
        try {
            logger.info("Creating new performance metric: {} for product: {}", metric.getMetricType(), metric.getProductId());
            EntityResponse<PerformanceMetric> response = entityService.save(metric);
            logger.info("Performance metric created with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create performance metric: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<PerformanceMetric>> getMetric(@PathVariable UUID id) {
        try {
            logger.info("Retrieving performance metric with ID: {}", id);
            EntityResponse<PerformanceMetric> response = entityService.getItem(id, PerformanceMetric.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve performance metric {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<PerformanceMetric>>> getAllMetrics() {
        try {
            logger.info("Retrieving all performance metrics");
            List<EntityResponse<PerformanceMetric>> metrics = entityService.getItems(
                PerformanceMetric.class,
                PerformanceMetric.ENTITY_NAME,
                PerformanceMetric.ENTITY_VERSION,
                null,
                null,
                null
            );
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Failed to retrieve performance metrics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<EntityResponse<PerformanceMetric>>> getMetricsByProduct(@PathVariable Long productId) {
        try {
            logger.info("Retrieving performance metrics for product: {}", productId);
            
            Condition productIdCondition = Condition.of("$.productId", "EQUALS", productId.toString());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(productIdCondition));
            
            List<EntityResponse<PerformanceMetric>> metrics = entityService.getItemsByCondition(
                PerformanceMetric.class,
                PerformanceMetric.ENTITY_NAME,
                PerformanceMetric.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Failed to retrieve performance metrics for product {}: {}", productId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntityResponse<PerformanceMetric>>> searchMetrics(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) String calculationPeriod,
            @RequestParam(required = false) String state) {
        try {
            logger.info("Searching performance metrics with filters - productId: {}, metricType: {}, period: {}, state: {}", 
                       productId, metricType, calculationPeriod, state);
            
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            
            List<Condition> conditions = new java.util.ArrayList<>();
            
            if (productId != null) {
                conditions.add(Condition.of("$.productId", "EQUALS", productId.toString()));
            }
            if (metricType != null && !metricType.trim().isEmpty()) {
                conditions.add(Condition.of("$.metricType", "EQUALS", metricType));
            }
            if (calculationPeriod != null && !calculationPeriod.trim().isEmpty()) {
                conditions.add(Condition.of("$.calculationPeriod", "EQUALS", calculationPeriod));
            }
            if (state != null && !state.trim().isEmpty()) {
                conditions.add(Condition.lifecycle("state", "EQUALS", state));
            }
            
            condition.setConditions(conditions);
            
            List<EntityResponse<PerformanceMetric>> metrics = entityService.getItemsByCondition(
                PerformanceMetric.class,
                PerformanceMetric.ENTITY_NAME,
                PerformanceMetric.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Failed to search performance metrics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<PerformanceMetric>> updateMetric(
            @PathVariable UUID id, 
            @RequestBody PerformanceMetric metric,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating performance metric with ID: {}, transition: {}", id, transition);
            
            EntityResponse<PerformanceMetric> response = entityService.update(id, metric, transition);
            
            logger.info("Performance metric updated with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update performance metric {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMetric(@PathVariable UUID id) {
        try {
            logger.info("Deleting performance metric with ID: {}", id);
            entityService.deleteById(id);
            logger.info("Performance metric deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete performance metric {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/transitions/{transitionName}")
    public ResponseEntity<EntityResponse<PerformanceMetric>> transitionMetric(
            @PathVariable UUID id, 
            @PathVariable String transitionName) {
        try {
            logger.info("Transitioning performance metric {} with transition: {}", id, transitionName);
            
            // Get current metric
            EntityResponse<PerformanceMetric> currentResponse = entityService.getItem(id, PerformanceMetric.class);
            PerformanceMetric metric = currentResponse.getData();
            
            // Update with transition
            EntityResponse<PerformanceMetric> response = entityService.update(id, metric, transitionName);
            
            logger.info("Performance metric transitioned with ID: {}, new state: {}", response.getId(), response.getState());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to transition performance metric {} with {}: {}", id, transitionName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
