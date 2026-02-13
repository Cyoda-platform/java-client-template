package com.cyoda.app.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "delivery_reports")
public class DeliveryReport {

    @Id
    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "total_subscribers")
    private int totalSubscribers;

    @Column(name = "emails_sent")
    private int emailsSent;

    @Column(name = "bounces")
    private int bounces;

    @Column(name = "failures")
    private int failures;

    public DeliveryReport() {
    }

    public DeliveryReport(LocalDate weekStartDate, int totalSubscribers, int emailsSent, int bounces, int failures) {
        this.weekStartDate = weekStartDate;
        this.totalSubscribers = totalSubscribers;
        this.emailsSent = emailsSent;
        this.bounces = bounces;
        this.failures = failures;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public int getTotalSubscribers() {
        return totalSubscribers;
    }

    public void setTotalSubscribers(int totalSubscribers) {
        this.totalSubscribers = totalSubscribers;
    }

    public int getEmailsSent() {
        return emailsSent;
    }

    public void setEmailsSent(int emailsSent) {
        this.emailsSent = emailsSent;
    }

    public int getBounces() {
        return bounces;
    }

    public void setBounces(int bounces) {
        this.bounces = bounces;
    }

    public int getFailures() {
        return failures;
    }

    public void setFailures(int failures) {
        this.failures = failures;
    }
}
