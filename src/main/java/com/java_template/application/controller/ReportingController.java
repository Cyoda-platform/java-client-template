package com.java_template.application.controller;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.interaction.version_1.Interaction;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    @Autowired
    private EntityService entityService;

    @GetMapping("/subscribers")
    public ResponseEntity<Map<String, Object>> getSubscriberMetrics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // Get all subscribers
            List<EntityResponse<Subscriber>> allSubscribers = entityService.findAll(
                Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION);
            
            // Calculate metrics
            long totalSubscribers = allSubscribers.size();
            long activeSubscribers = allSubscribers.stream()
                .filter(s -> Boolean.TRUE.equals(s.getData().getIsActive()))
                .count();
            
            long pendingSubscribers = allSubscribers.stream()
                .filter(s -> "pending".equals(s.getMetadata().getState()))
                .count();
            
            long unsubscribedSubscribers = allSubscribers.stream()
                .filter(s -> "unsubscribed".equals(s.getMetadata().getState()))
                .count();
            
            // Calculate weekly growth (simplified)
            LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
            long newSubscribersThisWeek = allSubscribers.stream()
                .filter(s -> s.getData().getSubscriptionDate() != null && 
                           s.getData().getSubscriptionDate().isAfter(oneWeekAgo))
                .count();
            
            double unsubscribeRate = totalSubscribers > 0 ? 
                (double) unsubscribedSubscribers / totalSubscribers : 0.0;
            
            double growthRate = totalSubscribers > 0 ? 
                (double) newSubscribersThisWeek / totalSubscribers : 0.0;
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalSubscribers", totalSubscribers);
            result.put("activeSubscribers", activeSubscribers);
            result.put("pendingSubscribers", pendingSubscribers);
            result.put("unsubscribedSubscribers", unsubscribedSubscribers);
            result.put("newSubscribersThisWeek", newSubscribersThisWeek);
            result.put("unsubscribeRate", Math.round(unsubscribeRate * 100.0) / 100.0);
            result.put("growthRate", Math.round(growthRate * 100.0) / 100.0);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", "An unexpected error occurred");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> getCampaignMetrics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // Get all campaigns
            List<EntityResponse<EmailCampaign>> allCampaigns = entityService.findAll(
                EmailCampaign.class, EmailCampaign.ENTITY_NAME, EmailCampaign.ENTITY_VERSION);
            
            // Calculate metrics
            long totalCampaigns = allCampaigns.size();
            long completedCampaigns = allCampaigns.stream()
                .filter(c -> "completed".equals(c.getMetadata().getState()))
                .count();
            
            long failedCampaigns = allCampaigns.stream()
                .filter(c -> "failed".equals(c.getMetadata().getState()))
                .count();
            
            // Calculate delivery metrics
            int totalEmailsSent = allCampaigns.stream()
                .filter(c -> c.getData().getSuccessfulSends() != null)
                .mapToInt(c -> c.getData().getSuccessfulSends())
                .sum();
            
            int totalEmailsFailed = allCampaigns.stream()
                .filter(c -> c.getData().getFailedSends() != null)
                .mapToInt(c -> c.getData().getFailedSends())
                .sum();
            
            double averageDeliveryRate = (totalEmailsSent + totalEmailsFailed) > 0 ? 
                (double) totalEmailsSent / (totalEmailsSent + totalEmailsFailed) : 0.0;
            
            // Get interaction count (simplified)
            List<EntityResponse<Interaction>> allInteractions = entityService.findAll(
                Interaction.class, Interaction.ENTITY_NAME, Interaction.ENTITY_VERSION);
            
            long totalInteractions = allInteractions.size();
            double engagementRate = totalEmailsSent > 0 ? 
                (double) totalInteractions / totalEmailsSent : 0.0;
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalCampaigns", totalCampaigns);
            result.put("completedCampaigns", completedCampaigns);
            result.put("failedCampaigns", failedCampaigns);
            result.put("averageDeliveryRate", Math.round(averageDeliveryRate * 1000.0) / 1000.0);
            result.put("totalEmailsSent", totalEmailsSent);
            result.put("totalInteractions", totalInteractions);
            result.put("engagementRate", Math.round(engagementRate * 1000.0) / 1000.0);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", "An unexpected error occurred");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/interactions")
    public ResponseEntity<Map<String, Object>> getInteractionMetrics(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // Get all interactions
            List<EntityResponse<Interaction>> allInteractions;
            
            if (type != null) {
                Condition typeCondition = Condition.of("$.interactionType", "EQUALS", type);
                SearchConditionRequest condition = new SearchConditionRequest();
                condition.setType("group");
                condition.setOperator("AND");
                condition.setConditions(List.of(typeCondition));
                
                allInteractions = entityService.getItemsByCondition(
                    Interaction.class, Interaction.ENTITY_NAME, Interaction.ENTITY_VERSION, condition, true);
            } else {
                allInteractions = entityService.findAll(
                    Interaction.class, Interaction.ENTITY_NAME, Interaction.ENTITY_VERSION);
            }
            
            // Calculate interaction metrics
            long totalInteractions = allInteractions.size();
            
            long emailOpens = allInteractions.stream()
                .filter(i -> "EMAIL_OPENED".equals(i.getData().getInteractionType()))
                .count();
            
            long emailClicks = allInteractions.stream()
                .filter(i -> "EMAIL_CLICKED".equals(i.getData().getInteractionType()))
                .count();
            
            // Calculate rates (simplified - would need total emails sent for accurate rates)
            double openRate = totalInteractions > 0 ? (double) emailOpens / totalInteractions : 0.0;
            double clickRate = totalInteractions > 0 ? (double) emailClicks / totalInteractions : 0.0;
            
            // Get top performing facts (simplified)
            Map<String, Long> factInteractionCounts = allInteractions.stream()
                .collect(Collectors.groupingBy(
                    i -> i.getData().getCatFactId(),
                    Collectors.counting()
                ));
            
            List<Map<String, Object>> topPerformingFacts = factInteractionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> fact = new HashMap<>();
                    fact.put("factId", entry.getKey());
                    fact.put("interactions", entry.getValue());
                    fact.put("engagementScore", Math.round((double) entry.getValue() / totalInteractions * 100.0) / 100.0);
                    
                    // Try to get fact text
                    try {
                        EntityResponse<CatFact> catFactResponse = entityService.findByBusinessId(
                            CatFact.class, CatFact.ENTITY_NAME, CatFact.ENTITY_VERSION, entry.getKey(), "id");
                        fact.put("factText", catFactResponse.getData().getFactText());
                    } catch (Exception e) {
                        fact.put("factText", "Unknown fact");
                    }
                    
                    return fact;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalInteractions", totalInteractions);
            result.put("emailOpens", emailOpens);
            result.put("emailClicks", emailClicks);
            result.put("openRate", Math.round(openRate * 1000.0) / 1000.0);
            result.put("clickRate", Math.round(clickRate * 1000.0) / 1000.0);
            result.put("topPerformingFacts", topPerformingFacts);
            
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
