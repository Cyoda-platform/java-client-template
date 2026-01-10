package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.certificate.version_1.Certificate;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
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
 * ABOUTME: CertificateController provides REST endpoints for managing certificate lifecycle
 * including creation, retrieval, updates, renewal, revocation, and deletion operations.
 */
@RestController
@RequestMapping("/ui/certificate")
@CrossOrigin(origins = "*")
public class CertificateController {

    private static final Logger logger = LoggerFactory.getLogger(CertificateController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CertificateController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new certificate
     * POST /ui/certificate
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Certificate>> createCertificate(@Valid @RequestBody Certificate certificate) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            EntityWithMetadata<Certificate> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, certificate.getCertificateId(), "certificateId", Certificate.class);

            if (existing != null) {
                logger.warn("Certificate with ID {} already exists", certificate.getCertificateId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Certificate already exists with ID: %s", certificate.getCertificateId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Certificate> response = entityService.create(certificate);
            logger.info("Certificate created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create certificate: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get certificate by technical UUID
     * GET /ui/certificate/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Certificate>> getCertificateById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Certificate> response = entityService.getById(id, modelSpec, Certificate.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve certificate with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get certificate by business identifier
     * GET /ui/certificate/business/{certificateId}
     */
    @GetMapping("/business/{certificateId}")
    public ResponseEntity<EntityWithMetadata<Certificate>> getCertificateByBusinessId(
            @PathVariable String certificateId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Certificate> response = entityService.findByBusinessId(
                    modelSpec, certificateId, "certificateId", Certificate.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve certificate with ID '%s': %s", certificateId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update certificate
     * PUT /ui/certificate/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Certificate>> updateCertificate(
            @PathVariable UUID id,
            @Valid @RequestBody Certificate certificate,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Certificate> response = entityService.update(id, certificate, transition);
            logger.info("Certificate updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update certificate with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all certificates with pagination
     * GET /ui/certificate?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Certificate>>> listCertificates(
            Pageable pageable,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Certificate.class, pointInTimeDate));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list certificates: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Renew certificate
     * POST /ui/certificate/{id}/renew
     */
    @PostMapping("/{id}/renew")
    public ResponseEntity<EntityWithMetadata<Certificate>> renewCertificate(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            EntityWithMetadata<Certificate> current = entityService.getById(id, modelSpec, Certificate.class);

            EntityWithMetadata<Certificate> response = entityService.update(id, current.entity(), "renew_certificate");
            logger.info("Certificate renewed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to renew certificate with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Revoke certificate
     * POST /ui/certificate/{id}/revoke
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<EntityWithMetadata<Certificate>> revokeCertificate(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            EntityWithMetadata<Certificate> current = entityService.getById(id, modelSpec, Certificate.class);
            
            Certificate cert = current.entity();
            cert.setRevokedDate(java.time.LocalDateTime.now());

            EntityWithMetadata<Certificate> response = entityService.update(id, cert, "revoke_certificate");
            logger.info("Certificate revoked with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to revoke certificate with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete certificate by technical UUID
     * DELETE /ui/certificate/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Certificate deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete certificate with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete certificate by business identifier
     * DELETE /ui/certificate/business/{certificateId}
     */
    @DeleteMapping("/business/{certificateId}")
    public ResponseEntity<Void> deleteCertificateByBusinessId(@PathVariable String certificateId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Certificate.ENTITY_NAME).withVersion(Certificate.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, certificateId, "certificateId", Certificate.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Certificate deleted with business ID: {}", certificateId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete certificate with ID '%s': %s", certificateId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

