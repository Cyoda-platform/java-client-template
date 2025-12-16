package com.java_template.application.controller;

import com.java_template.application.entity.portfolio.version_1.Portfolio;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST Controller for Portfolio entity
 * Provides portfolio management and valuation endpoints
 */
@RestController
@RequestMapping("/ui/portfolios")
@CrossOrigin(origins = "*")
public class PortfolioController {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PortfolioController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Portfolio>> createPortfolio(@RequestBody Portfolio portfolio) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Portfolio.ENTITY_NAME).withVersion(Portfolio.ENTITY_VERSION);
            EntityWithMetadata<Portfolio> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, portfolio.getPortfolioId(), "portfolioId", Portfolio.class);

            if (existing != null) {
                logger.warn("Portfolio with ID {} already exists", portfolio.getPortfolioId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Portfolio already exists with ID: %s", portfolio.getPortfolioId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Portfolio> response = entityService.create(portfolio);
            logger.info("Portfolio created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create portfolio: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Portfolio>> getPortfolioById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Portfolio.ENTITY_NAME).withVersion(Portfolio.ENTITY_VERSION);
            EntityWithMetadata<Portfolio> response = entityService.getById(id, modelSpec, Portfolio.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve portfolio: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Portfolio>> updatePortfolio(
            @PathVariable UUID id,
            @RequestBody Portfolio portfolio,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Portfolio> response = entityService.update(id, portfolio, transition);
            logger.info("Portfolio updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update portfolio: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/mark-to-market")
    public ResponseEntity<EntityWithMetadata<Portfolio>> markToMarket(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Portfolio.ENTITY_NAME).withVersion(Portfolio.ENTITY_VERSION);
            EntityWithMetadata<Portfolio> current = entityService.getById(id, modelSpec, Portfolio.class);

            EntityWithMetadata<Portfolio> response = entityService.update(id, current.entity(), "mark_to_market");
            logger.info("Portfolio marked to market with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to mark portfolio to market: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Portfolio deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete portfolio: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

