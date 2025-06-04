package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final List<String> PAIRS = List.of("BTC/USD", "ETH/USD");
    private static final List<String> SIDES = List.of("buy", "sell");

    @Resource
    private EntityService entityService;

    private ScheduledExecutorService orderGeneratorScheduler;
    private volatile boolean generatingOrders = false;

    @PostConstruct
    public void init() {
        startOrderGeneration();
    }

    private void startOrderGeneration() {
        if (generatingOrders) {
            logger.info("Order generation already running.");
            return;
        }
        generatingOrders = true;
        orderGeneratorScheduler = Executors.newSingleThreadScheduledExecutor();
        orderGeneratorScheduler.scheduleAtFixedRate(() -> {
            try {
                generateAndStoreRandomOrders(10);
            } catch (Exception e) {
                logger.error("Error generating random orders", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
        logger.info("Started continuous order generation: 10 orders/second");
    }

    private void generateAndStoreRandomOrders(int count) {
        Random rnd = new Random();
        List<ObjectNode> newOrders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ObjectNode orderNode = entityService.createObjectNode();
            String side = SIDES.get(rnd.nextInt(SIDES.size()));
            String pair = PAIRS.get(rnd.nextInt(PAIRS.size()));
            double price = generatePriceForPair(pair, rnd);
            double amount = 0.01 + (rnd.nextDouble() * 10);
            Instant timestamp = Instant.now();

            orderNode.put("side", side);
            orderNode.put("price", price);
            orderNode.put("amount", amount);
            orderNode.put("pair", pair);
            orderNode.put("timestamp", timestamp.toString());

            newOrders.add(orderNode);
        }
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                "Order",
                ENTITY_VERSION,
                newOrders
        );
        idsFuture.whenComplete((ids, ex) -> {
            if (ex != null) {
                logger.error("Failed to store generated orders", ex);
            } else {
                logger.info("Stored {} new orders with technicalIds: {}", ids.size(), ids);
            }
        });
    }

    private double generatePriceForPair(String pair, Random rnd) {
        switch (pair) {
            case "BTC/USD":
                return 29000 + rnd.nextDouble() * 2000;
            case "ETH/USD":
                return 1800 + rnd.nextDouble() * 400;
            default:
                return 100 + rnd.nextDouble() * 1000;
        }
    }

    @GetMapping("/orders/summary")
    public CompletableFuture<ResponseEntity<SummaryResponse>> getOrdersSummary(
            @RequestParam(required = false)
            @Pattern(regexp = "BTC/USD|ETH/USD", message = "Pair must be BTC/USD or ETH/USD")
                    String pairFilter) {
        return entityService.getItems("Order", ENTITY_VERSION)
                .thenApply(ordersNode -> {
                    Map<String, SummaryAccumulator> summaryMap = new HashMap<>();
                    for (String pair : PAIRS) {
                        summaryMap.put(pair, new SummaryAccumulator());
                    }
                    for (int i = 0; i < ordersNode.size(); i++) {
                        ObjectNode order = (ObjectNode) ordersNode.get(i);
                        String pair = order.get("pair").asText();
                        if (pairFilter == null || pairFilter.equals(pair)) {
                            SummaryAccumulator acc = summaryMap.get(pair);
                            if (acc != null) {
                                acc.count++;
                                acc.totalAmount += order.get("amount").asDouble();
                                acc.totalPrice += order.get("price").asDouble();
                            }
                        }
                    }
                    List<PairSummary> pairSummaries = new ArrayList<>();
                    for (String pair : PAIRS) {
                        SummaryAccumulator acc = summaryMap.get(pair);
                        double avgPrice = acc.count > 0 ? acc.totalPrice / acc.count : 0.0;
                        pairSummaries.add(new PairSummary(pair, acc.totalAmount, avgPrice));
                    }
                    return ResponseEntity.ok(new SummaryResponse(pairSummaries));
                })
                .exceptionally(ex -> {
                    logger.error("Failed to get orders for summary", ex);
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get orders summary");
                });
    }

    private static class SummaryAccumulator {
        int count = 0;
        double totalAmount = 0;
        double totalPrice = 0;
    }

    @PostMapping("/report/send")
    public CompletableFuture<ResponseEntity<ReportResponse>> sendHourlyReport(@RequestBody @Valid ReportRequest request) {
        ObjectNode reportRequestNode = entityService.createObjectNode();
        reportRequestNode.put("email", request.getEmail());
        return entityService.addItem(
                "ReportRequest",
                ENTITY_VERSION,
                reportRequestNode
        ).thenApply(uuid -> ResponseEntity.ok(new ReportResponse("email_sent", "Report email sent to " + request.getEmail())))
         .exceptionally(ex -> {
             logger.error("Failed to send report request", ex);
             throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send report");
         });
    }

    private CompletableFuture<Void> sendEmailAsync(String to, String subject, String body) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Sending email to {}\nSubject: {}\nBody:\n{}", to, subject, body);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleException(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getReason());
        err.put("status", ex.getStatusCode().toString());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PairSummary {
        private String pair;
        private double totalAmount;
        private double averagePrice;
    }

    @Data
    @AllArgsConstructor
    public static class SummaryResponse {
        private List<PairSummary> summary;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReportRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class ReportResponse {
        private String status;
        private String message;
    }
}