# DataSource Entity

## Overview
Represents a CSV data source that can be downloaded and analyzed. Manages the lifecycle of data fetching and processing.

## Attributes
- **dataSourceId** (String, required): Unique identifier for the data source
- **url** (String, required): URL of the CSV file to download
- **name** (String, required): Human-readable name for the data source
- **description** (String, optional): Description of the data source
- **lastFetchTime** (LocalDateTime, optional): Timestamp of last successful fetch
- **lastAnalysisTime** (LocalDateTime, optional): Timestamp of last analysis
- **recordCount** (Integer, optional): Number of records in the dataset
- **fileSize** (Long, optional): Size of the downloaded file in bytes
- **checksum** (String, optional): Checksum for data integrity verification

## Relationships
- One DataSource can generate multiple Reports
- DataSource state is managed internally via entity.meta.state

## Validation Rules
- dataSourceId must not be null
- url must be a valid HTTP/HTTPS URL
- name must not be empty
