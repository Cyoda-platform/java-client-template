# DataSource Entity

## Overview
Represents a CSV data source that can be downloaded from a URL for analysis.

## Attributes
- **sourceId**: Unique identifier for the data source
- **url**: URL to download the CSV data from
- **fileName**: Name of the downloaded file
- **downloadedAt**: Timestamp when data was downloaded
- **fileSize**: Size of downloaded file in bytes
- **status**: Current processing status (mapped to entity.meta.state)

## Relationships
- Triggers DataAnalysis entity when download completes
- Referenced by EmailNotification for report context

## Business Rules
- URL must be accessible and return valid CSV data
- File size must be within acceptable limits
- Download must complete successfully before analysis can begin
