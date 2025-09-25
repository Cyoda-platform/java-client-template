package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.protocol.version_1.Protocol;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ui/protocol")
@CrossOrigin(origins = "*")
public class ProtocolController {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProtocolController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Protocol>> createProtocol(@RequestBody Protocol protocol) {
        logger.info("Creating new protocol: {}", protocol.getProtocolId());
        try {
            EntityWithMetadata<Protocol> savedProtocol = entityService.save(protocol, Protocol.class);
            return ResponseEntity.ok(savedProtocol);
        } catch (Exception e) {
            logger.error("Error creating protocol: {}", protocol.getProtocolId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Protocol>> getProtocolById(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Protocol> protocol = entityService.findById(createModelSpec(), id, Protocol.class);
            return protocol != null ? ResponseEntity.ok(protocol) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving protocol by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/business/{protocolId}")
    public ResponseEntity<EntityWithMetadata<Protocol>> getProtocolByBusinessId(@PathVariable String protocolId) {
        try {
            EntityWithMetadata<Protocol> protocol = entityService.findByBusinessId(createModelSpec(), "protocolId", protocolId, Protocol.class);
            return protocol != null ? ResponseEntity.ok(protocol) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving protocol by business ID: {}", protocolId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Protocol>> updateProtocol(
            @PathVariable UUID id, 
            @RequestBody Protocol protocol,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Protocol> existingProtocol = entityService.findById(createModelSpec(), id, Protocol.class);
            if (existingProtocol == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Protocol> updatedProtocol = (transition != null && !transition.trim().isEmpty()) 
                ? entityService.save(protocol, transition, Protocol.class)
                : entityService.save(protocol, Protocol.class);
            
            return ResponseEntity.ok(updatedProtocol);
        } catch (Exception e) {
            logger.error("Error updating protocol: {}", protocol.getProtocolId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Protocol>>> getAllProtocols() {
        try {
            GroupCondition emptyCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>());
            List<EntityWithMetadata<Protocol>> results = entityService.search(createModelSpec(), emptyCondition, Protocol.class);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all protocols", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProtocol(@PathVariable UUID id) {
        try {
            entityService.delete(createModelSpec(), id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting protocol with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private ModelSpec createModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Protocol.ENTITY_NAME);
        modelSpec.setVersion(Protocol.ENTITY_VERSION);
        return modelSpec;
    }
}
