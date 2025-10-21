package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.company.version_1.Company;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Company entity providing CRUD operations
 * and search functionality for the CRM system.
 */
@RestController
@RequestMapping("/ui/company")
@CrossOrigin(origins = "*")
public class CompanyController {

    private static final Logger logger = LoggerFactory.getLogger(CompanyController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompanyController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new company
     * POST /ui/company
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Company>> createCompany(@Valid @RequestBody Company company) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Company.ENTITY_NAME).withVersion(Company.ENTITY_VERSION);
            EntityWithMetadata<Company> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, company.getCompanyId(), "companyId", Company.class);

            if (existing != null) {
                logger.warn("Company with business ID {} already exists", company.getCompanyId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Company already exists with ID: %s", company.getCompanyId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Company> response = entityService.create(company);
            logger.info("Company created with ID: {}", response.metadata().getId());

            // Build Location header for the created resource
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create company", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create company: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get company by technical ID
     * GET /ui/company/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Company>> getCompanyById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Company.ENTITY_NAME).withVersion(Company.ENTITY_VERSION);
            EntityWithMetadata<Company> company = entityService.getById(id, modelSpec, Company.class);
            return ResponseEntity.ok(company);
        } catch (Exception e) {
            logger.error("Failed to get company by ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Company not found with ID: %s", id)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get company by business ID
     * GET /ui/company/business/{businessId}
     */
    @GetMapping("/business/{businessId}")
    public ResponseEntity<EntityWithMetadata<Company>> getCompanyByBusinessId(@PathVariable String businessId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Company.ENTITY_NAME).withVersion(Company.ENTITY_VERSION);
            EntityWithMetadata<Company> company = entityService.findByBusinessId(
                    modelSpec, businessId, "companyId", Company.class);
            return ResponseEntity.ok(company);
        } catch (Exception e) {
            logger.error("Failed to get company by business ID: {}", businessId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Company not found with business ID: %s", businessId)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update company
     * PUT /ui/company/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Company>> updateCompany(
            @PathVariable UUID id, 
            @Valid @RequestBody Company company,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Company> response = entityService.update(id, company, transition);
            logger.info("Company updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update company with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update company: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete company
     * DELETE /ui/company/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Company deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete company with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Company not found with ID: %s", id)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Search companies
     * GET /ui/company/search
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Company>>> searchCompanies(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Company.ENTITY_NAME).withVersion(Company.ENTITY_VERSION);
            List<QueryCondition> conditions = new ArrayList<>();

            if (name != null && !name.trim().isEmpty()) {
                SimpleCondition nameCondition = new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(name));
                conditions.add(nameCondition);
            }

            if (industry != null && !industry.trim().isEmpty()) {
                SimpleCondition industryCondition = new SimpleCondition()
                        .withJsonPath("$.industry")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(industry));
                conditions.add(industryCondition);
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Company>> companies = conditions.isEmpty() 
                ? entityService.findAll(modelSpec, Company.class)
                : entityService.search(modelSpec, groupCondition, Company.class);

            return ResponseEntity.ok(companies);
        } catch (Exception e) {
            logger.error("Failed to search companies", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                String.format("Failed to search companies: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Archive company (transition to archived state)
     * POST /ui/company/{id}/archive
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<EntityWithMetadata<Company>> archiveCompany(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Company.ENTITY_NAME).withVersion(Company.ENTITY_VERSION);
            EntityWithMetadata<Company> company = entityService.getById(id, modelSpec, Company.class);
            EntityWithMetadata<Company> response = entityService.update(id, company.entity(), "archive_company");
            logger.info("Company archived with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to archive company with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to archive company: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Reactivate company (transition from archived to active state)
     * POST /ui/company/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Company>> reactivateCompany(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Company.ENTITY_NAME).withVersion(Company.ENTITY_VERSION);
            EntityWithMetadata<Company> company = entityService.getById(id, modelSpec, Company.class);
            EntityWithMetadata<Company> response = entityService.update(id, company.entity(), "reactivate_company");
            logger.info("Company reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to reactivate company with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to reactivate company: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}
