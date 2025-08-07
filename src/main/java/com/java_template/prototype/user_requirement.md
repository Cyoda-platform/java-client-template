# Complete Requirement Specification

## Overview  
Develop a **Java-based application** that ingests Nobel laureates data from the **OpenDataSoft API** and distributes relevant updates to subscribers.

## Core Entities

### 1. Job Entity  
- Responsible for managing the lifecycle of a data ingestion process.
- **States:**  
  - SCHEDULED  
  - INGESTING  
  - SUCCEEDED  
  - FAILED  
  - NOTIFIED_SUBSCRIBERS  
- **Responsibilities:**  
  - Schedule ingestion from the API.  
  - Transition through well-defined workflow states.  
  - Trigger downstream processing on successful ingestion.  
  - Notify all active subscribers upon job completion (success or failure).

### 2. Laureate Entity  
- Represents a Nobel Prize laureate.  
- **Attributes to Extract & Store:**  
  - Personal Info: `id`, `firstname`, `surname`, `gender`, `born`, `died`  
  - Origin Details: `borncountry`, `borncountrycode`, `borncity`  
  - Award Info: `year`, `category`, `motivation`  
  - Affiliation: `name`, `city`, `country`  
- **Processors:**  
  - Validation Processor: Ensure required fields are non-null and values conform to expected formats.  
  - Enrichment Processor: Enhance or normalize data, e.g., calculating age, standardizing country codes, etc.

### 3. Subscriber Entity  
- Defines recipients who are notified when new laureates are ingested.  
- **Responsibilities:**  
  - Hold contact information (email, webhook, etc.).  
  - Receive notifications upon job completion.  
  - Do not participate in workflow orchestration.

## Workflow Summary  
```
SCHEDULED → INGESTING → (SUCCEEDED | FAILED) → NOTIFIED_SUBSCRIBERS
```

- Each job must strictly follow this lifecycle to ensure reliable ingestion and notification.

## Data Source  
- **API Endpoint:**  
  `https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records`  
- This endpoint returns structured JSON data representing Nobel Prize laureates.

## Technical Requirements  
- Implement **asynchronous processing** for ingestion and notification.  
- Provide **error handling** and **logging** per state transition.  
- Use JSON parsing libraries such as **Jackson** or **Gson** for processing API responses.  
- Configure job scheduling using libraries like **Quartz** or **Spring Scheduler**.