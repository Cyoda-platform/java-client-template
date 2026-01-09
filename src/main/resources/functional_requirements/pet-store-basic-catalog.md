# Pet Store Data Collector - Basic Catalog

Scope
- Ingest product listings from Pet Store API and maintain an up-to-date catalog.

Ingestion
- Poll Pet Store API every 1 minute for updates.

Stored fields (Basic)
- id
- name
- category
- price
- availability
- last_updated

Non-functional
- Support at least 10k products.
- Retry on transient failures with exponential backoff (max 5 retries).
- Log ingestion metrics (latency, failures, items processed).

Deliverables
- Entity model for Product with listed fields.
- Workflow for ingest and update processing.
- API endpoints for querying products and recent updates.
- Basic UI to list products (optional)
