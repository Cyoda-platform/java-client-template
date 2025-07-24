```markdown
# Finnish Companies Data Retrieval and Enrichment Application - Requirements

## 1. Overview
Develop an application to:
- Retrieve data from the Finnish Companies Registry (PRH Avoindata API) based on a given company name.
- Filter out inactive company names.
- Enrich the retrieved data by fetching the Legal Entity Identifier (LEI) from web sources.

---

## 2. Functional Requirements

### 2.1 Data Retrieval
- Query the Finnish Companies Registry API:  
  `https://avoindata.prh.fi/opendata-ytj-api/`
- Input: Company name or partial name.
- Return: Exact or closest matching companies.

### 2.2 Filtering
- Filter out inactive company names after retrieval.
- A company is **active** if its business status is marked as active in the registry.
- If multiple names exist for an entity, keep only those marked active.

### 2.3 LEI Data Enrichment
- Search the web for the Legal Entity Identifier (LEI) of each active company.
- Sources: Official LEI registries or reliable financial data sources.
- Add the LEI to the output if found.
- If LEI is not found, mark the field as `"Not Available"`.

### 2.4 Output
- Output formats supported: JSON or CSV.
- Include these fields for each company:
  - Company Name
  - Business ID
  - Company Type
  - Registration Date
  - Status (Active/Inactive)
  - LEI (or `"Not Available"` if missing)

---

## 3. API Details (PRH Avoindata YTJ API v3)

- **Base URL:** `https://avoindata.prh.fi/opendata-ytj-api/v3`
  
- **Endpoint:** `/companies` (GET)

- **Query Parameters:**
  - `name` (string): Company name (search current, previous, parallel, or auxiliary names)
  - `location` (string): Town or city
  - `businessId` (string): Business ID
  - `companyForm` (string enum): Company form codes (e.g., AOY, ASH, OY, etc.)
  - `mainBusinessLine` (string): Main line of business (Statistics Finland TOL 2008 code or text)
  - `registrationDateStart` (string, date yyyy-mm-dd): Start of registration date range
  - `registrationDateEnd` (string, date yyyy-mm-dd): End of registration date range
  - `postCode` (string): Postal code
  - `businessIdRegistrationStart` (string, date yyyy-mm-dd): Start of Business ID grant date range
  - `businessIdRegistrationEnd` (string, date yyyy-mm-dd): End of Business ID grant date range
  - `page` (integer): Result page number for pagination
  
- **Response Codes:**
  - `200 OK`: Successful response with company data.
  - `400 Bad Request`: Invalid request parameters.
  - `429 Too Many Requests`: Rate limiting exceeded.
  - `500 Internal Server Error`: Server error.
  - `503 Service Unavailable`: Server cannot process request.

---

## 4. Cyoda Design Considerations

- Use **Java 21 Spring Boot** as the chosen technology stack.
- Architect the system as an **event-driven system** leveraging Cyoda stack:
  - Core entity: **Company Entity**
  - Workflow triggered by an event (e.g., "Company Search Requested").
  - Integration with **Trino** for advanced querying if needed.
  - Dynamic workflows managing retrieval, filtering, and enrichment steps.
- Each entity holds state and undergoes transitions through the workflow stages:
  1. Data Retrieval from PRH API
  2. Filtering inactive names
  3. LEI enrichment from external sources
  4. Preparing final output

---

## 5. External LEI Data Enrichment Sources (suggested)

- Official LEI registries such as:
  - GLEIF (Global Legal Entity Identifier Foundation) API: https://www.gleif.org/en/lei-data/gleif-api
  - Other reliable financial data APIs or web scraping if API unavailable.

---

## 6. Output Example (JSON)

```json
[
  {
    "companyName": "Example Oy",
    "businessId": "1234567-8",
    "companyType": "OY",
    "registrationDate": "2010-05-12",
    "status": "Active",
    "lei": "5493001KJTIIGC8Y1R12"
  },
  {
    "companyName": "Another Company Oy",
    "businessId": "8765432-1",
    "companyType": "OY",
    "registrationDate": "2015-08-20",
    "status": "Active",
    "lei": "Not Available"
  }
]
```

---

## 7. Summary

- Input: Company name (partial or full).
- Process:
  - Query PRH Companies API.
  - Filter active companies only.
  - Enrich with LEI from external trusted sources.
- Output: Structured JSON or CSV including requested fields and LEI info.
- Built on Java 21 Spring Boot within Cyoda architecture principles for event-driven workflows.

---

# References

- [PRH Avoindata YTJ API Documentation](https://avoindata.prh.fi/opendata-ytj-api/)
- [Creative Commons Nimeä 4.0 License](https://creativecommons.org/licenses/by/4.0/)
- [GLEIF LEI API](https://www.gleif.org/en/lei-data/gleif-api)

---

*This document preserves all business logic and technical requirements specified by the user.*
```