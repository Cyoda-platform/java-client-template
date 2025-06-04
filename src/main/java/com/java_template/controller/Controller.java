package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@Component
@RequestMapping("/cyoda-entity")
public class CyodaEntityController {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Order";
    private static final String HOURLY_REPORT_ENTITY = "HourlyReport";

    private static final ObjectMapper mapper = new ObjectMapper();

    public CyodaEntityController(EntityService entityService) {
        this.entityService = entityService;
    }

    // Controller just calls simulateOrdersBatch with raw data without workflow
    @PostConstruct
    public void startSimulation() {
        log.info("Starting initial order simulation batch...");
        simulateOrdersBatch(50);
    }

    @Scheduled(fixedRate = 10000)
    public void simulateOrdersPeriodically() {
        log.info("Periodic order simulation triggered");
        simulateOrdersBatch(10);
    }

    // Instead of generating report here, we just trigger an empty report entity.
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void triggerHourlyReport() {
        log.info("Triggering hourly report generation");
        ObjectNode reportNode = mapper.createObjectNode();
        String hourKey = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0).toString();
        reportNode.put("reportTimestamp", hourKey);
        reportNode.putObject("totalsByPair");

        entityService.addItem(HOURLY_REPORT_ENTITY, ENTITY_VERSION, reportNode)
                .exceptionally(ex -> {
                    log.error("Failed to add HourlyReport entity", ex);
                    return null;
                });
    }

    private void simulateOrdersBatch(int count) {
        Random rnd = new Random();
        List<String> pairs = Arrays.asList("BTC-USD", "ETH-USD", "XRP-USD");
        List<String> users = Arrays.asList("user1", "user2", "user3", "user4");

        List<ObjectNode> batch = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ObjectNode orderNode = mapper.createObjectNode();
            orderNode.put("orderId", UUID.randomUUID().toString());
            orderNode.put("timestamp", Instant.now().minusSeconds(rnd.nextInt(3600)).toString());
            orderNode.put("price", BigDecimal.valueOf(1000 + rnd.nextDouble() * 50000).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
            orderNode.put("amount", BigDecimal.valueOf(0.01 + rnd.nextDouble() * 5).setScale(4, BigDecimal.ROUND_HALF_UP).toString());
            orderNode.put("pair", pairs.get(rnd.nextInt(pairs.size())));
            orderNode.put("side", rnd.nextBoolean() ? "BUY" : "SELL");
            orderNode.put("status", rnd.nextDouble() < 0.8 ? "EXECUTED" : "REJECTED");
            orderNode.put("userId", users.get(rnd.nextInt(users.size())));

            batch.add(orderNode);
        }

        entityService.addItems(ENTITY_NAME, ENTITY_VERSION, batch)
                .exceptionally(ex -> {
                    log.error("Failed to add batch orders", ex);
                    return null;
                });
    }

    // Workflow functions removed - no workflow argument usage
    
    // Email sending method removed from workflow
}