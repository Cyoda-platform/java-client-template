#\!/bin/bash

# Create Site Controller
cat > "src/main/java/com/java_template/application/controller/SiteController.java" << 'CTRL_EOF'
package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.site.version_1.Site;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ui/site")
@CrossOrigin(origins = "*")
public class SiteController {

    private static final Logger logger = LoggerFactory.getLogger(SiteController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SiteController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Site>> createSite(@RequestBody Site site) {
        logger.info("Creating new site: {}", site.getSiteId());
        try {
            EntityWithMetadata<Site> savedSite = entityService.save(site, Site.class);
            return ResponseEntity.ok(savedSite);
        } catch (Exception e) {
            logger.error("Error creating site: {}", site.getSiteId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Site>> getSiteById(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Site> site = entityService.findById(createModelSpec(), id, Site.class);
            return site \!= null ? ResponseEntity.ok(site) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving site by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/business/{siteId}")
    public ResponseEntity<EntityWithMetadata<Site>> getSiteByBusinessId(@PathVariable String siteId) {
        try {
            EntityWithMetadata<Site> site = entityService.findByBusinessId(createModelSpec(), "siteId", siteId, Site.class);
            return site \!= null ? ResponseEntity.ok(site) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving site by business ID: {}", siteId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Site>> updateSite(
            @PathVariable UUID id, 
            @RequestBody Site site,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Site> existingSite = entityService.findById(createModelSpec(), id, Site.class);
            if (existingSite == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Site> updatedSite = (transition \!= null && \!transition.trim().isEmpty()) 
                ? entityService.save(site, transition, Site.class)
                : entityService.save(site, Site.class);
            
            return ResponseEntity.ok(updatedSite);
        } catch (Exception e) {
            logger.error("Error updating site: {}", site.getSiteId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Site>>> getAllSites() {
        try {
            GroupCondition emptyCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>());
            List<EntityWithMetadata<Site>> results = entityService.search(createModelSpec(), emptyCondition, Site.class);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all sites", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSite(@PathVariable UUID id) {
        try {
            entityService.delete(createModelSpec(), id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting site with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private ModelSpec createModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Site.ENTITY_NAME);
        modelSpec.setVersion(Site.ENTITY_VERSION);
        return modelSpec;
    }
}
CTRL_EOF

# Create Investigator Controller
cat > "src/main/java/com/java_template/application/controller/InvestigatorController.java" << 'CTRL_EOF'
package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.investigator.version_1.Investigator;
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
@RequestMapping("/ui/investigator")
@CrossOrigin(origins = "*")
public class InvestigatorController {

    private static final Logger logger = LoggerFactory.getLogger(InvestigatorController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InvestigatorController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Investigator>> createInvestigator(@RequestBody Investigator investigator) {
        logger.info("Creating new investigator: {}", investigator.getInvestigatorId());
        try {
            EntityWithMetadata<Investigator> savedInvestigator = entityService.save(investigator, Investigator.class);
            return ResponseEntity.ok(savedInvestigator);
        } catch (Exception e) {
            logger.error("Error creating investigator: {}", investigator.getInvestigatorId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Investigator>> getInvestigatorById(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Investigator> investigator = entityService.findById(createModelSpec(), id, Investigator.class);
            return investigator \!= null ? ResponseEntity.ok(investigator) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving investigator by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/business/{investigatorId}")
    public ResponseEntity<EntityWithMetadata<Investigator>> getInvestigatorByBusinessId(@PathVariable String investigatorId) {
        try {
            EntityWithMetadata<Investigator> investigator = entityService.findByBusinessId(createModelSpec(), "investigatorId", investigatorId, Investigator.class);
            return investigator \!= null ? ResponseEntity.ok(investigator) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving investigator by business ID: {}", investigatorId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Investigator>> updateInvestigator(
            @PathVariable UUID id, 
            @RequestBody Investigator investigator,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Investigator> existingInvestigator = entityService.findById(createModelSpec(), id, Investigator.class);
            if (existingInvestigator == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Investigator> updatedInvestigator = (transition \!= null && \!transition.trim().isEmpty()) 
                ? entityService.save(investigator, transition, Investigator.class)
                : entityService.save(investigator, Investigator.class);
            
            return ResponseEntity.ok(updatedInvestigator);
        } catch (Exception e) {
            logger.error("Error updating investigator: {}", investigator.getInvestigatorId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Investigator>>> getAllInvestigators() {
        try {
            GroupCondition emptyCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>());
            List<EntityWithMetadata<Investigator>> results = entityService.search(createModelSpec(), emptyCondition, Investigator.class);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all investigators", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestigator(@PathVariable UUID id) {
        try {
            entityService.delete(createModelSpec(), id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting investigator with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private ModelSpec createModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Investigator.ENTITY_NAME);
        modelSpec.setVersion(Investigator.ENTITY_VERSION);
        return modelSpec;
    }
}
CTRL_EOF

# Create Protocol Controller
cat > "src/main/java/com/java_template/application/controller/ProtocolController.java" << 'CTRL_EOF'
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
            return protocol \!= null ? ResponseEntity.ok(protocol) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving protocol by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/business/{protocolId}")
    public ResponseEntity<EntityWithMetadata<Protocol>> getProtocolByBusinessId(@PathVariable String protocolId) {
        try {
            EntityWithMetadata<Protocol> protocol = entityService.findByBusinessId(createModelSpec(), "protocolId", protocolId, Protocol.class);
            return protocol \!= null ? ResponseEntity.ok(protocol) : ResponseEntity.notFound().build();
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

            EntityWithMetadata<Protocol> updatedProtocol = (transition \!= null && \!transition.trim().isEmpty()) 
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
CTRL_EOF

echo "Created remaining controllers"
