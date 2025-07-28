```markdown
# Finnish Companies Data Retrieval and Enrichment Application - Requirements Document

## 1. Overview
This application will retrieve a subset of data from the Finnish Companies Registry (PRH Avoindata API) based on a given company name, filter out inactive company names, and enrich the retrieved data by fetching the Legal Entity Identifier (LEI) from the web.

---

## 2. Functional Requirements

### 2.1 Data Retrieval
- The application must query the Finnish Companies Registry API (PRH Avoindata API) available at:  
  `https://avoindata.prh.fi/opendata-ytj-api/`
- The input to the application must be a company name or partial company name.
- The API endpoint to be used is:  
  `GET /companies` with the query parameter `name` for company name search.
- The application must return only exact or closest matching companies according to the API response.
- Support pagination if more than 100 results are returned (via the `page` query parameter).

### 2.2 Filtering
- After retrieving data, the application must filter out inactive company names.
- A company is considered active if its business status is marked as active in the registry.
- If multiple names exist for a single entity, only retain those marked as active.

### 2.3 LEI Data Enrichment
- For each active company, the application must search the web to find the company's Legal Entity Identifier (LEI).
- LEI should be fetched from official LEI registries or reliable financial data sources.
- If an LEI exists for the company, it must be added to the output.
- If no LEI is found, the LEI field should be marked as `"Not Available"`.

### 2.4 Output
- The final output must be provided in a structured JSON or CSV format.
- Output fields must include:
  - Company Name
  - Business ID
  - Company Type
  - Registration Date
  - Status (Active/Inactive)
  - LEI (if available, otherwise "Not Available")

---

## 3. Technical Details

### 3.1 PRH Avoindata API Specification (OpenAPI 3.0.1 excerpt)
- **Base URL:** `https://avoindata.prh.fi/opendata-ytj-api/v3`
- **Endpoint:** `/companies`
- **HTTP Method:** GET
- **Query Parameters:**
  - `name` (string): Company name or partial name to search.
  - `page` (integer, optional): For paginated results.
  - Additional optional filters available but not required for this application (e.g., location, businessId, companyForm, registrationDateStart, registrationDateEnd, etc.)
- **Response:** JSON containing company data, including status and business ID.

### 3.2 Business Status Filtering
- Use the business status field from the API response to determine if a company is active.
- Only companies with active status should be retained.

### 3.3 LEI Retrieval
- LEI data must be sourced from official or reputable web sources.
- Possible sources include:
  - Global LEI Foundation (GLEIF) API: https://www.gleif.org/en/lei-data/gleif-data
  - Other official financial registries or APIs that provide LEI lookup by company name or business ID.
- The enrichment process is an additional API/crawl step after retrieving and filtering companies from PRH API.

---

## 4. Summary

| Feature                  | Details                                                                                 |
|--------------------------|-----------------------------------------------------------------------------------------|
| Input                    | Company name or partial name                                                           |
| Data Source              | Finnish Companies Registry (PRH Avoindata API)                                         |
| Filtering                | Remove inactive companies based on business status                                     |
| Enrichment               | Fetch Legal Entity Identifier (LEI) from official LEI registry or reliable sources     |
| Output Format            | JSON or CSV                                                                            |
| Output Fields            | Company Name, Business ID, Company Type, Registration Date, Status, LEI                 |
| LEI Field if Not Found   | Mark as `"Not Available"`                                                              |
| API Endpoint             | `GET https://avoindata.prh.fi/opendata-ytj-api/v3/companies?name={companyName}`        |
| Pagination Handling      | Support `page` parameter if more than 100 results                                     |

---

## 5. Notes
- The design must consider event-driven architecture with entities and workflows as per Cyoda design values.
- The core entity is a "Company" with workflows triggered by data retrieval and enrichment events.
- Integration with external APIs (PRH Avoindata and LEI registries) must handle errors and rate limiting gracefully.
- Ensure compliance with API licensing: Creative Commons Nimeä 4.0 (https://creativecommons.org/licenses/by/4.0/).

---

# End of Requirements
```