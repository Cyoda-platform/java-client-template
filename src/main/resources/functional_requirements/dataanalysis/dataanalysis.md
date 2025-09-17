# DataAnalysis Entity

## Overview
Handles analysis of downloaded CSV data using pandas-style operations to generate insights and reports.

## Attributes
- **analysisId**: Unique identifier for the analysis
- **dataSourceId**: Reference to the DataSource entity
- **analysisType**: Type of analysis performed (e.g., "housing_market_summary")
- **reportData**: Generated analysis results and insights
- **analysisStartedAt**: Timestamp when analysis began
- **analysisCompletedAt**: Timestamp when analysis finished
- **status**: Current analysis status (mapped to entity.meta.state)

## Relationships
- Depends on DataSource entity for input data
- Triggers EmailNotification when analysis completes
- Contains analysis results for email reports

## Business Rules
- Analysis can only start after successful data download
- Must generate meaningful insights from housing data
- Report data must be formatted for email delivery
