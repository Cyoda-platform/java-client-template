package com.cyoda.app.service;

import com.cyoda.app.entity.DeliveryReport;
import com.cyoda.app.repository.DeliveryReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReportingService {
    private final DeliveryReportRepository repository;

    public ReportingService(DeliveryReportRepository repository) {
        this.repository = repository;
    }

    public void recordWeeklyReport(LocalDate weekStart, int totalSubscribers, int emailsSent, int bounces, int failures) {
        DeliveryReport report = new DeliveryReport(weekStart, totalSubscribers, emailsSent, bounces, failures);
        repository.save(report);
    }
}
