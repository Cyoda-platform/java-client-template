# Product Performance Analysis and Reporting System - Functional Requirements

## 1. Overview
This document captures the functional requirements for an MVP focused on generating weekly PDF summaries of product performance using data from the Pet Store API.

## 2. Purpose
Provide the sales team with a reliable weekly summary of product performance metrics, delivered via email to victoria.sagdieva@cyoda.com every Monday.

## 3. Features

### 3.1 Data Extraction
- Scheduled automated job (every Monday) to fetch product sales and stock data from Pet Store API (https://petstore.swagger.io/#/).
- Support JSON and XML data formats.
- Basic retry mechanism and error logging.

### 3.2 Product Performance Analysis
- Compute KPIs: sales volume, revenue per product, inventory turnover rates.
- Aggregate data by category and weekly time windows.
- Identify underperforming products using a simple threshold-based rule.

### 3.3 Report Generation
- Generate a weekly PDF summary with:
  - Overview of sales trends
  - Top selling products
  - Slow-moving inventory and restocking suggestions
  - Basic charts for key KPIs
- Fixed PDF template for MVP (no runtime template editing).

### 3.4 Email Notification
- Email the PDF report to victoria.sagdieva@cyoda.com with a short summary in the email body.

### 3.5 Non-functional Requirements
- Secure handling of data and logs.
- Basic scalability considerations and clear logging for troubleshooting.

## 4. Acceptance Criteria
- Weekly PDF report is generated and sent to the recipient.
- KPIs are computed and reflected in the report.
- Failures are logged and retriable.
