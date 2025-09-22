# Report Entity

## Overview
Represents an analysis report generated from CSV data. Contains analysis results and metadata about the report generation process.

## Attributes
- **reportId** (String, required): Unique identifier for the report
- **dataSourceId** (String, required): Reference to the source data
- **title** (String, required): Report title
- **summary** (String, optional): Executive summary of findings
- **analysisResults** (Map<String, Object>, optional): Key-value pairs of analysis results
- **generatedAt** (LocalDateTime, optional): Timestamp when report was generated
- **reportFormat** (String, optional): Format of the report (HTML, PDF, etc.)
- **recipientCount** (Integer, optional): Number of recipients who received this report

## Relationships
- Each Report belongs to one DataSource
- Reports can be sent to multiple Subscribers
- Report state is managed internally via entity.meta.state

## Validation Rules
- reportId must not be null
- dataSourceId must not be null
- title must not be empty
