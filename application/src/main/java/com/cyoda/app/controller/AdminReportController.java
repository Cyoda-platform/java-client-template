package com.cyoda.app.controller;

import com.cyoda.app.entity.DeliveryReport;
import com.cyoda.app.repository.DeliveryReportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/admin/reports")
public class AdminReportController {
    private final DeliveryReportRepository repository;

    public AdminReportController(DeliveryReportRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/weekly")
    public ResponseEntity<?> getWeeklyReport(@RequestParam("weekStart") String weekStart) {
        LocalDate date = LocalDate.parse(weekStart);
        Optional<DeliveryReport> maybe = repository.findById(date);
        return maybe.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
