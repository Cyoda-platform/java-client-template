# Customer Management — Additional Functional & Non-Functional Requirements

This document supplements the primary customer_management requirements with operational, security, integrations, and rollout considerations for production readiness.

## 1. Data Model & Consistency
- Canonical Customer record: id (UUID), customer_id (external id), name, email, phone, status, metadata, created_at, updated_at, version
- Use optimistic concurrency control (version field) for updates to avoid lost updates during concurrent edits.
- Enforce unique constraints on email and customer_id at the database level.
- Soft delete vs hard delete: terminated customers are soft-deleted (flagged) and retained for a configurable retention period (default 365 days); provides ability to purge on policy.

## 2. Scalability & Performance
- Horizontal scalability for API and UI tiers via stateless services behind load balancers.
- Read replica strategy for read-heavy endpoints (Customer List) to keep list queries <500ms under normal load.
- Asynchronous processors (verification, notifications, archiving) run on worker pools with configurable concurrency and backpressure controls.
- Target: support 10k active customers with 100 TPS onboarding requests across cluster.

## 3. Reliability & Resilience
- Retry policies for external calls: exponential backoff with jitter; idempotency keys for retrying verification and deactivation tasks.
- Circuit breakers for flaky integrations (verification provider, notification service) with monitoring and alerting on tripped breakers.
- Graceful degradation: if verification provider is unavailable, allow manual review and queue requests for retry.

## 4. Security & Compliance
- Authentication: OAuth2 / OIDC with RBAC. Short-lived access tokens and refresh tokens with rotation.
- Authorization: RBAC enforcing least privilege; audit checks for privileged operations (suspend, terminate).
- Data protection: PII (email, phone) encrypted at rest and masked in logs and UI where possible.
- Audit logging: Immutable audit trail for all state transitions and sensitive field changes. Must include actor, timestamp, transition, payload diff.
- GDPR / Data Subject Requests: endpoints for data export and deletion (respecting retention policies); maintain consent records if required.

## 5. Observability & Monitoring
- Metrics: count of customers by status, transition frequency, processor success/failure rates, API latency percentiles (p50/p95/p99), queue lengths.
- Distributed tracing across processors and workflow transitions for debugging end-to-end flows (trace IDs propagated via messages).
- Structured logs (JSON) with request IDs and correlation IDs; retention policy configurable (30 days default for debug logs).
- Alerts: on high error rates (>1%), increased retry counts, slow verification responses (>5s), or queue backlog growth.

## 6. Backup & Disaster Recovery
- Daily backups of primary data with point-in-time recovery for last 7 days.
- Recovery RTO: <4 hours for critical services; RPO: <1 hour for transactional data.
- Restore playbook for bringing up customer data and reconnecting workers.

## 7. Integrations & Extensibility
- Verification Provider: pluggable adapter interface; support multiple providers for failover.
- Notification Service: pluggable channels (email, SMS, webhook) with templates stored in the app.
- External CRM sync: optional worker that synchronizes customer data to external CRM systems; configurable per customer via metadata flags.
- Eventing: emit domain events for major transitions (customer.created, customer.verified, customer.suspended, customer.terminated) to an event bus (Kafka / PubSub) for downstream consumers.

## 8. API Design & Contracts
- Versioned APIs (v1, v2) with backward compatibility guarantees for minor releases.
- Use JSON Schema for request/response validation; return clear error codes for client handling.
- Rate limiting: default 100 requests/minute per API key; stricter limits for public endpoints.

## 9. Testing Strategy
- Unit tests for processors, transition logic, and criterion functions.
- Integration tests with a test verification provider (mock) covering onboarding → verification → active flows.
- End-to-end tests in a staging environment covering UI flows, API calls, and worker behavior.
- Chaos tests for external provider failures and network partitions.

## 10. CI/CD & Releases
- CI: run linters, unit tests, integration tests (with mocks), and static analysis on PRs.
- CD: Canary releases for backend services; feature flags for major UI/behavior changes.
- Rollback: automated rollback on health-check failures during deployment.

## 11. Operational Runbook
- On-call rotations and escalation paths for outages related to verification or processing queues.
- Playbooks for high-severity incidents (verification provider outage, data corruption, prolonged queue backlog).
- Routine maintenance windows for schema migrations with zero-downtime strategies (backfill workers, column additions with default values, feature toggles).

## 12. Data Migration & Versioning
- Migrations must be idempotent, reversible where possible, and tested against a snapshot of production-sized data.
- Versioned entity folders and workflow versions (already in use) to support roll-forward/roll-back of business logic.

## 13. Access & UI Requirements (Additional)
- Bulk import/export UI for onboarding customers from CSV with validation preview and dry-run mode.
- Role-based views: Support Agent view with quick filters for pending verification and suspended customers.
- Audit viewer with filters (by actor, date range, transition type).

## 14. Acceptance Criteria (Additional)
- System supports failover of verification provider with <5 minutes of manual configuration change.
- Eventing delivers transition events to the event bus with at-least-once delivery semantics; downstream retries handled idempotently.
- Backup restore tested annually and documented.

## 15. Open Risks & Mitigations
- Risk: Verification provider downtime impacting onboarding throughput. Mitigation: queue requests and provide manual verification path; add multi-provider adapter.
- Risk: Data privacy regulations differing by region. Mitigation: configurable retention and data residency options; consult legal for region-specific policies.

---
Generated on: 2025-12-12
