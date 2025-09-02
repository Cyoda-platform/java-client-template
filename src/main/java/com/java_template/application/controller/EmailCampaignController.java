package com.java_template.application.controller;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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
@RequestMapping("/api/campaigns")
public class EmailCampaignController {

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCampaign(@RequestBody EmailCampaign campaign) {
        try {
            // Set default values if not provided
            if (campaign.getCampaignName() == null) {
                campaign.setCampaignName("Week of " + LocalDateTime.now().toLocalDate().toString());
            }
            if (campaign.getSubject() == null) {
                campaign.setSubject("Your Weekly Cat Fact!");
            }
            if (campaign.getEmailTemplate() == null) {
                campaign.setEmailTemplate("weekly_cat_fact_template");
            }
            
            EntityResponse<EmailCampaign> response = entityService.save(campaign);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", response.getData().getId());
            result.put("campaignName", response.getData().getCampaignName());
            result.put("catFactId", response.getData().getCatFactId());
            result.put("scheduledDate", response.getData().getScheduledDate());
            result.put("state", response.getMetadata().getState());
            result.put("totalSubscribers", response.getData().getTotalSubscribers());
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

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCampaign(
            @PathVariable String id, 
            @RequestBody Map<String, Object> request) {
        try {
            UUID campaignId = UUID.fromString(id);
            EntityResponse<EmailCampaign> campaignResponse = entityService.getItem(campaignId, EmailCampaign.class);
            
            EmailCampaign campaign = campaignResponse.getData();
            
            // Update fields if provided
            if (request.containsKey("campaignName")) {
                campaign.setCampaignName((String) request.get("campaignName"));
            }
            if (request.containsKey("scheduledDate")) {
                // Parse the date string to LocalDateTime
                String dateStr = (String) request.get("scheduledDate");
                campaign.setScheduledDate(LocalDateTime.parse(dateStr));
            }
            if (request.containsKey("subject")) {
                campaign.setSubject((String) request.get("subject"));
            }
            if (request.containsKey("catFactId")) {
                campaign.setCatFactId((String) request.get("catFactId"));
            }
            
            String transitionName = (String) request.get("transitionName");
            
            EntityResponse<EmailCampaign> updatedResponse = entityService.update(
                campaignResponse.getMetadata().getId(), campaign, transitionName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", updatedResponse.getData().getId());
            result.put("state", updatedResponse.getMetadata().getState());
            result.put("scheduledDate", updatedResponse.getData().getScheduledDate());
            result.put("totalSubscribers", updatedResponse.getData().getTotalSubscribers());
            result.put("technicalId", updatedResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Campaign not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCampaign(@PathVariable String id) {
        try {
            UUID campaignId = UUID.fromString(id);
            EntityResponse<EmailCampaign> campaignResponse = entityService.getItem(campaignId, EmailCampaign.class);
            
            EmailCampaign campaign = campaignResponse.getData();
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", campaign.getId());
            result.put("campaignName", campaign.getCampaignName());
            result.put("catFactId", campaign.getCatFactId());
            result.put("scheduledDate", campaign.getScheduledDate());
            result.put("sentDate", campaign.getSentDate());
            result.put("state", campaignResponse.getMetadata().getState());
            result.put("totalSubscribers", campaign.getTotalSubscribers());
            result.put("successfulSends", campaign.getSuccessfulSends());
            result.put("failedSends", campaign.getFailedSends());
            result.put("subject", campaign.getSubject());
            result.put("technicalId", campaignResponse.getMetadata().getId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "RESOURCE_NOT_FOUND");
            error.put("message", "Campaign not found");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCampaigns(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<EntityResponse<EmailCampaign>> campaigns;
            
            if (state != null || startDate != null || endDate != null) {
                SearchConditionRequest condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                
                List<Condition> conditions = new java.util.ArrayList<>();
                
                if (state != null) {
                    conditions.add(Condition.of("$.meta.state", "EQUALS", state));
                }
                if (startDate != null) {
                    conditions.add(Condition.of("$.scheduledDate", "GREATER_THAN_OR_EQUAL", startDate));
                }
                if (endDate != null) {
                    conditions.add(Condition.of("$.scheduledDate", "LESS_THAN_OR_EQUAL", endDate));
                }
                
                condition.setConditions(conditions);
                
                campaigns = entityService.getItemsByCondition(
                    EmailCampaign.class, EmailCampaign.ENTITY_NAME, EmailCampaign.ENTITY_VERSION, condition, true);
            } else {
                campaigns = entityService.getItems(
                    EmailCampaign.class, EmailCampaign.ENTITY_NAME, EmailCampaign.ENTITY_VERSION, size, page, null);
            }
            
            List<Map<String, Object>> campaignList = campaigns.stream()
                .map(response -> {
                    Map<String, Object> camp = new HashMap<>();
                    camp.put("id", response.getData().getId());
                    camp.put("campaignName", response.getData().getCampaignName());
                    camp.put("scheduledDate", response.getData().getScheduledDate());
                    camp.put("state", response.getMetadata().getState());
                    camp.put("successfulSends", response.getData().getSuccessfulSends());
                    camp.put("totalSubscribers", response.getData().getTotalSubscribers());
                    camp.put("technicalId", response.getMetadata().getId());
                    return camp;
                })
                .toList();
            
            Map<String, Object> result = new HashMap<>();
            result.put("campaigns", campaignList);
            result.put("totalCount", campaignList.size());
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
