package com.cyoda.app.repository;

import com.cyoda.app.entity.DeliveryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DeliveryReportRepository extends JpaRepository<DeliveryReport, LocalDate> {
}
