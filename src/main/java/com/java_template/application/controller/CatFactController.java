package com.java_template.application.controller;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/catfacts")
public class CatFactController {

    @Autowired
    private EntityService entityService;

    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchCatFact(@RequestBody Map<String, String> request) {
        try {
            CatFact catFact = new CatFact();
            
            // In a real implementation, we would call the Cat Fact API here
            // For now, create a sample fact
            catFact.setFactText("Cats have 32 muscles in each ear.");
            catFact.setLength(32);
            catFact.setSource(request.getOrDefault("source", "catfact.ninja"));
            catFact.setRetrievedDate(LocalDateTime.now());
            catFact.setUsageCount(0);
            
            EntityResponse<CatFact> response = entityService.save(catFact);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", response.getData().getId());
            result.put("factText", response.getData().getFactText());
            result.put("length", response.getData().getLength());
            result.put("source", response.getData().getSource());
            result.put("retrievedDate", response.getData().getRetrievedDate());
            result.put("state", response.getMetadata().getState());
            result.put("technicalId", response.getMetadata().getId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", e.getMessage());
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCatFact(
            @PathVariable String id, 
            @RequestBody Map<String, Object> request) {
        try {
            UUID factId = UUID.fromString(id);
            EntityResponse<CatFact> factResponse = entityService.getItem(factId, CatFact.class);
            
            CatFact catFact = factResponse.getData();
            
            // Update fields if provided
            if (request.containsKey("factText")) {
                catFact.setFactText((String) request.get("factText"));
                catFact.setLength(catFact.getFactText().length());
            }
            if (request.containsKey("notes")) {
                // Notes could be stored in metadata or preferences
                // For now, we'll just log them
            }
            
            String transitionName = (String) request.get("transitionName");
            
            EntityResponse<CatFact> updatedResponse = entityService.update(
                factResponse.getMetadata().getId(), catFact, transitionName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", updatedResponse.getData().getId());
            result.put("factText", updatedResponse.getData().getFactText());
            result.put("state", updatedResponse.getMetadata().getState());
            result.put("usageCount", updatedResponse.getData().getUsageCount());
            result.put("technicalId", updatedResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Cat fact not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCatFact(@PathVariable String id) {
        try {
            UUID factId = UUID.fromString(id);
            EntityResponse<CatFact> factResponse = entityService.getItem(factId, CatFact.class);
            
            CatFact catFact = factResponse.getData();
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", catFact.getId());
            result.put("factText", catFact.getFactText());
            result.put("length", catFact.getLength());
            result.put("source", catFact.getSource());
            result.put("retrievedDate", catFact.getRetrievedDate());
            result.put("usageCount", catFact.getUsageCount());
            result.put("lastUsedDate", catFact.getLastUsedDate());
            result.put("state", factResponse.getMetadata().getState());
            result.put("technicalId", factResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Cat fact not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCatFacts(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Integer minLength,
            @RequestParam(required = false) Integer maxLength,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<EntityResponse<CatFact>> catFacts;
            
            if (state != null || minLength != null || maxLength != null) {
                SearchConditionRequest condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                
                List<Condition> conditions = new java.util.ArrayList<>();
                
                if (state != null) {
                    conditions.add(Condition.of("$.meta.state", "EQUALS", state));
                }
                if (minLength != null) {
                    conditions.add(Condition.of("$.length", "GREATER_THAN_OR_EQUAL", minLength));
                }
                if (maxLength != null) {
                    conditions.add(Condition.of("$.length", "LESS_THAN_OR_EQUAL", maxLength));
                }
                
                condition.setConditions(conditions);
                
                catFacts = entityService.getItemsByCondition(
                    CatFact.class, CatFact.ENTITY_NAME, CatFact.ENTITY_VERSION, condition, true);
            } else {
                catFacts = entityService.getItems(
                    CatFact.class, CatFact.ENTITY_NAME, CatFact.ENTITY_VERSION, size, page, null);
            }
            
            List<Map<String, Object>> factList = catFacts.stream()
                .map(response -> {
                    Map<String, Object> fact = new HashMap<>();
                    fact.put("id", response.getData().getId());
                    fact.put("factText", response.getData().getFactText());
                    fact.put("length", response.getData().getLength());
                    fact.put("state", response.getMetadata().getState());
                    fact.put("usageCount", response.getData().getUsageCount());
                    fact.put("technicalId", response.getMetadata().getId());
                    return fact;
                })
                .toList();
            
            Map<String, Object> result = new HashMap<>();
            result.put("facts", factList);
            result.put("totalCount", factList.size());
            result.put("page", page);
            result.put("size", size);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", "An unexpected error occurred");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
