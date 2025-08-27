Help me create cms+crm backend
Context
Use docs.cyoda.net as the primary reference for Cyoda’s EDBMS approach: entities with finite-state workflows; transitions guarded by criteria; processors for logic; event-driven recursion to a stable state.

Follow the Cyoda Workflow Configuration Guide for designing workflows: states, transitions (manual/automated), criteria (prefer function), processors (SYNC/ASYNC_*), and execution semantics.

Keep in mind the project philosophy: declarative, deterministic, entity-centric systems are better for both developers and AI-assisted implementation.

High-Level Goal
Implement a Cyoda-native backend for the Cyoda website that provides:
A CMS for posts (blog, article, howto, guide) with authoring, versioning, publishing, and AI-friendly artifacts.

A CRM for user registration, identity, email verification, consents (double opt-in, opt-out), and profile management.

Use Cyoda’s existing service endpoints (Saving/Getting Data + SQL Querying). Do not introduce a separate REST layer; the frontend will call Cyoda directly.
Non-Functional Requirements
Auth: Auth0 (OIDC). Email verification required before marketing consent is active (double opt-in).

Language: en-GB initially (design extensible to future locales).

AI readiness: Precompute normalized plaintext chunks and embeddings (store vectors now; search/answers API can be added later).

Performance & Caching: Serve immutable artifacts via Cloudflare; index/lookup queries cacheable with s-maxage + stale-while-revalidate.

Compliance: GDPR export/erasure; consent evidence retained; audit trail of admin actions.