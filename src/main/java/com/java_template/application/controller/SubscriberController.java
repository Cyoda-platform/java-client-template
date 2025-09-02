package com.java_template.application.controller;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/subscribers")
public class SubscriberController {

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSubscriber(@RequestBody Subscriber subscriber) {
        try {
            // Set default values
            subscriber.setSubscriptionDate(LocalDateTime.now());
            subscriber.setIsActive(false); // Pending verification
            
            EntityResponse<Subscriber> response = entityService.save(subscriber);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", response.getData().getId());
            result.put("email", response.getData().getEmail());
            result.put("firstName", response.getData().getFirstName());
            result.put("lastName", response.getData().getLastName());
            result.put("subscriptionDate", response.getData().getSubscriptionDate());
            result.put("isActive", response.getData().getIsActive());
            result.put("state", response.getMetadata().getState());
            result.put("technicalId", response.getMetadata().getId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "VALIDATION_ERROR");
            error.put("message", e.getMessage());
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{email}/verify")
    public ResponseEntity<Map<String, Object>> verifySubscriber(
            @PathVariable String email, 
            @RequestBody Map<String, String> request) {
        try {
            EntityResponse<Subscriber> subscriberResponse = entityService.findByBusinessId(
                Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, email, "email");
            
            Subscriber subscriber = subscriberResponse.getData();
            subscriber.setIsActive(true);
            
            // Clear verification token if present
            if (subscriber.getPreferences() != null) {
                subscriber.getPreferences().remove("verificationToken");
            }
            
            EntityResponse<Subscriber> updatedResponse = entityService.update(
                subscriberResponse.getMetadata().getId(), subscriber, "transition_to_active");
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", updatedResponse.getData().getId());
            result.put("email", updatedResponse.getData().getEmail());
            result.put("isActive", updatedResponse.getData().getIsActive());
            result.put("state", updatedResponse.getMetadata().getState());
            result.put("message", "Email verified successfully");
            result.put("technicalId", updatedResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Subscriber not found or verification failed");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @PutMapping("/{email}")
    public ResponseEntity<Map<String, Object>> updateSubscriber(
            @PathVariable String email, 
            @RequestBody Map<String, Object> request) {
        try {
            EntityResponse<Subscriber> subscriberResponse = entityService.findByBusinessId(
                Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, email, "email");
            
            Subscriber subscriber = subscriberResponse.getData();
            
            // Update fields if provided
            if (request.containsKey("firstName")) {
                subscriber.setFirstName((String) request.get("firstName"));
            }
            if (request.containsKey("lastName")) {
                subscriber.setLastName((String) request.get("lastName"));
            }
            if (request.containsKey("preferences")) {
                subscriber.setPreferences((Map<String, Object>) request.get("preferences"));
            }
            
            String transitionName = (String) request.get("transitionName");
            
            EntityResponse<Subscriber> updatedResponse = entityService.update(
                subscriberResponse.getMetadata().getId(), subscriber, transitionName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", updatedResponse.getData().getId());
            result.put("email", updatedResponse.getData().getEmail());
            result.put("firstName", updatedResponse.getData().getFirstName());
            result.put("lastName", updatedResponse.getData().getLastName());
            result.put("isActive", updatedResponse.getData().getIsActive());
            result.put("state", updatedResponse.getMetadata().getState());
            result.put("technicalId", updatedResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Subscriber not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Map<String, Object>> unsubscribe(
            @PathVariable String email, 
            @RequestBody(required = false) Map<String, String> request) {
        try {
            EntityResponse<Subscriber> subscriberResponse = entityService.findByBusinessId(
                Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, email, "email");
            
            Subscriber subscriber = subscriberResponse.getData();
            subscriber.setIsActive(false);
            
            // Add unsubscribe reason to preferences
            if (request != null && request.containsKey("reason")) {
                if (subscriber.getPreferences() == null) {
                    subscriber.setPreferences(new HashMap<>());
                }
                subscriber.getPreferences().put("unsubscribeReason", request.get("reason"));
                subscriber.getPreferences().put("unsubscribeDate", LocalDateTime.now().toString());
            }
            
            entityService.update(subscriberResponse.getMetadata().getId(), subscriber, "transition_to_unsubscribed");
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Successfully unsubscribed");
            result.put("unsubscribeDate", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Subscriber not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping("/{email}")
    public ResponseEntity<Map<String, Object>> getSubscriber(@PathVariable String email) {
        try {
            EntityResponse<Subscriber> subscriberResponse = entityService.findByBusinessId(
                Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, email, "email");
            
            Subscriber subscriber = subscriberResponse.getData();
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", subscriber.getId());
            result.put("email", subscriber.getEmail());
            result.put("firstName", subscriber.getFirstName());
            result.put("lastName", subscriber.getLastName());
            result.put("subscriptionDate", subscriber.getSubscriptionDate());
            result.put("isActive", subscriber.getIsActive());
            result.put("state", subscriberResponse.getMetadata().getState());
            result.put("preferences", subscriber.getPreferences());
            result.put("technicalId", subscriberResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Subscriber not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSubscribers(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<EntityResponse<Subscriber>> subscribers;
            
            if (state != null || active != null) {
                SearchConditionRequest condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                
                if (state != null) {
                    Condition stateCondition = Condition.of("$.meta.state", "EQUALS", state);
                    condition.setConditions(List.of(stateCondition));
                }
                if (active != null) {
                    Condition activeCondition = Condition.of("$.isActive", "EQUALS", active);
                    if (condition.getConditions() != null) {
                        condition.getConditions().add(activeCondition);
                    } else {
                        condition.setConditions(List.of(activeCondition));
                    }
                }
                
                subscribers = entityService.getItemsByCondition(
                    Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, condition, true);
            } else {
                subscribers = entityService.getItems(
                    Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, size, page, null);
            }
            
            List<Map<String, Object>> subscriberList = subscribers.stream()
                .map(response -> {
                    Map<String, Object> sub = new HashMap<>();
                    sub.put("id", response.getData().getId());
                    sub.put("email", response.getData().getEmail());
                    sub.put("firstName", response.getData().getFirstName());
                    sub.put("isActive", response.getData().getIsActive());
                    sub.put("state", response.getMetadata().getState());
                    sub.put("technicalId", response.getMetadata().getId());
                    return sub;
                })
                .toList();
            
            Map<String, Object> result = new HashMap<>();
            result.put("subscribers", subscriberList);
            result.put("totalCount", subscriberList.size());
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
