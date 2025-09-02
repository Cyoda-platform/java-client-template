package com.java_template.application.controller;

import com.java_template.application.entity.tag.version_1.Tag;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    @Autowired
    private EntityService entityService;

    @GetMapping
    public ResponseEntity<List<Tag>> getAllTags(
            @RequestParam(required = false) Boolean active) {
        
        try {
            List<EntityResponse<Tag>> tagResponses = entityService.findAll(
                Tag.class, 
                Tag.ENTITY_NAME, 
                Tag.ENTITY_VERSION
            );
            
            List<Tag> tags = tagResponses.stream()
                .map(EntityResponse::getData)
                .toList();
            
            return ResponseEntity.ok(tags);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tag> getTagById(@PathVariable UUID id) {
        try {
            EntityResponse<Tag> tagResponse = entityService.getItem(id, Tag.class);
            return ResponseEntity.ok(tagResponse.getData());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTag(@RequestBody Tag tag) {
        try {
            EntityResponse<Tag> savedTag = entityService.save(tag);
            
            Map<String, Object> response = Map.of(
                "id", savedTag.getMetadata().getId(),
                "name", savedTag.getData().getName(),
                "state", savedTag.getMetadata().getState(),
                "message", "Tag created and activated successfully"
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTag(
            @PathVariable UUID id,
            @RequestBody Tag tag,
            @RequestParam(required = false) String transition) {
        
        try {
            EntityResponse<Tag> updatedTag = entityService.update(id, tag, transition);
            
            Map<String, Object> response = Map.of(
                "id", updatedTag.getMetadata().getId(),
                "name", updatedTag.getData().getName(),
                "state", updatedTag.getMetadata().getState(),
                "message", transition != null ? 
                    "Tag updated with transition: " + transition : 
                    "Tag updated successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTag(@PathVariable UUID id) {
        try {
            // Get the tag first to update it with archive transition
            EntityResponse<Tag> tagResponse = entityService.getItem(id, Tag.class);
            Tag tag = tagResponse.getData();
            
            EntityResponse<Tag> archivedTag = entityService.update(id, tag, "archive_tag");
            
            Map<String, Object> response = Map.of(
                "id", archivedTag.getMetadata().getId(),
                "message", "Tag archived successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
