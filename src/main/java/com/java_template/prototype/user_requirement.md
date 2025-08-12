# Complete Requirements for Java-Based Nobel Laureates Data Ingestion Application

## Overview
- Develop a Java-based application.
- Ingest Nobel laureates data from the OpenDataSoft API.
- Distribute relevant updates to subscribers.
- Model three core entities: Job, Laureate, and Subscriber.
- Implement a basic workflow engine for data processing and notification.

## Data Source
- API Endpoint:  
  `https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records`
- This endpoint returns structured JSON data representing Nobel Prize laureates.

## Core Components

### Job Entity
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

### Laureate Entity
- Represents a Nobel Prize laureate.
- Example input JSON structure:

  ```json
  {
    "id": 853,
    "firstname": "Akira",
    "surname": "Suzuki",
    "born": "1930-09-12",
    "died": null,
    "borncountry": "Japan",
    "borncountrycode": "JP",
    "borncity": "Mukawa",
    "gender": "male",
    "year": "2010",
    "category": "Chemistry",
    "motivation": "\"for palladium-catalyzed cross couplings in organic synthesis\"",
    "name": "Hokkaido University",
    "city": "Sapporo",
    "country": "Japan"
  }
  ```
- **Attributes to Extract & Store:**  
  - Personal Info: `id`, `firstname`, `surname`, `gender`, `born`, `died`  
  - Origin Details: `borncountry`, `borncountrycode`, `borncity`  
  - Award Info: `year`, `category`, `motivation`  
  - Affiliation: `name`, `city`, `country`
- **Processors:**  
  - Validation Processor: Ensure required fields are non-null and values conform to expected formats.  
  - Enrichment Processor: Enhance or normalize data, e.g., calculating age, standardizing country codes, etc.

### Subscriber Entity
- Defines recipients who are notified when new laureates are ingested.
- **Responsibilities:**  
  - Hold contact information (email, webhook, etc.).  
  - Receive notifications upon job completion.  
  - Do NOT participate in workflow orchestration.

## Workflow Summary

```mermaid
graph TD
A[SCHEDULED] --> B[INGESTING]
B --> C1[SUCCEEDED]
B --> C2[FAILED]
C1 --> D[NOTIFIED_SUBSCRIBERS]
C2 --> D
```

- Each job must strictly follow this lifecycle to ensure reliable ingestion and notification.

## Additional Notes
- Use asynchronous processing for ingestion and notification.
- Provide error handling and logging for each state transition.
- Use JSON parsing libraries like **Jackson** or **Gson** for processing API responses.
- Configure job scheduling using libraries like **Quartz** or **Spring Scheduler**.