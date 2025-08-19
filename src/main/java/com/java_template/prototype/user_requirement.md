# Requirement Specification (extracted)

- Title: Build an app to download and analyze London houses data and email a report to subscribers

- Functional requirements:
  - Build an app to download data from https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv
  - Analyze the downloaded data with pandas
  - Send a report via email to subscribers

- Technical references and tools (explicitly specified):
  - Data source URL: https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv
  - Analysis library: pandas

- Actors/Recipients:
  - Subscribers (to receive the email report)

- Sequence / Order (implied):
  1. Download data from the specified URL
  2. Analyze the data using pandas
  3. Send a report via email to subscribers

(Note: All business logic, technical details, and references are preserved exactly as provided.)