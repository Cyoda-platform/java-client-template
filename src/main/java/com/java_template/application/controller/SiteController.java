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
            return site != null ? ResponseEntity.ok(site) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving site by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/business/{siteId}")
    public ResponseEntity<EntityWithMetadata<Site>> getSiteByBusinessId(@PathVariable String siteId) {
        try {
            EntityWithMetadata<Site> site = entityService.findByBusinessId(createModelSpec(), "siteId", siteId, Site.class);
            return site != null ? ResponseEntity.ok(site) : ResponseEntity.notFound().build();
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

            EntityWithMetadata<Site> updatedSite = (transition != null && !=transition.trim().isEmpty()) 
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
