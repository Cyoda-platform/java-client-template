# Customer Management — Functional & UI Requirements

## Overview
This document defines the UI and functional requirements for the Customer Management system centered on the Customer entity and the CustomerLifecycle workflow. It covers screens, flows, form validation, roles, API interactions, notifications, and acceptance criteria.

## Goals
- Provide an intuitive UI to manage customers through their lifecycle: onboarding, verification, active, suspended, termination.
- Support both admin and user roles with appropriate permissions.
- Integrate with verification and notification processors defined in the workflow.
- Responsive and accessible web UI (desktop + mobile).

## Actors & Roles
- Admin: Full access to create, edit, suspend, reinstate, terminate customers, view audit logs.
- Support Agent: Can view and update customer details, suspend or flag for review.
- Customer (self-serve): Can view and update own basic profile (name, email, phone) and request deactivation.

## Primary Screens
1. Dashboard (Admin)
   - Summary cards: Total customers, Active, Suspended, Pending Verification, Terminated.
   - Quick actions: Create Customer, Bulk actions, Search.

2. Customer List
   - Table columns: ID, Customer ID, Name, Email, Phone, Status, Last Updated, Actions.
   - Pagination, sort (by name, status, last updated), server-side search and filters (status, created date range).
   - Row actions: View, Edit, Suspend/Reinstate, Terminate, Export.

3. Customer Detail / Profile
   - Read-only header: Name, Customer ID, Status badge (initial_state, onboarding, verification_pending, verified, active, suspended, termination_pending, terminated).
   - Tabs: Overview, Activity & Audit Log, Verification, Integrations.
   - Overview tab fields: id, customer_id, name, email, phone, created_at, updated_at, custom metadata.
   - Action buttons: Edit, Start Onboarding, Request Verification, Suspend, Reinstate, Terminate (availability depends on state and role).

4. Onboarding Wizard
   - Multi-step form: Basic Info -> Attach Documents (if required) -> Review -> Submit.
   - Inline validation and progress indicator.
   - On submit: triggers start_onboarding -> request_verification processors.

5. Verification Status Screen
   - Shows verification request history, current status, last checked, provider response payload preview.
   - Retry verification button (if allowed) — triggers verification flow in workflow.

6. Create / Edit Customer Form
   - Fields: customer_id (optional manual override), name (required), email (required, email format), phone (optional, E.164 format), metadata (key/value), tags.
   - Validation rules: required fields, email regex, phone regex, unique constraints for customer_id/email enforced server-side.
   - Save behavior: Save triggers update_details transition (if in active) or relevant transition.

7. Admin Console / Audit & Logs
   - View processors calls, verification attempts, change history (who, what, when).
   - Export logs and filtered views.

## UI Interactions & Workflow Mapping
- Creating a customer puts it in initial_state.
- Start Onboarding (system/manual) → onboarding state; request_verification transition triggers sendVerificationRequest processor.
- verification_pending state shows verification UI; automatic transitions (verification_success / verification_failed) based on criterion function results.
- verified → activate transitions to active and triggers grantInitialAccess processor.
- From active, admins can suspend/reinstate/terminate using UI actions which call the corresponding workflow transitions.

## APIs & Integration Points
- GET /api/customers (list, filters, pagination)
- POST /api/customers (create customer)
- GET /api/customers/{id}
- PATCH /api/customers/{id} (partial update)
- POST /api/customers/{id}/transitions (body: { "transition": "suspend" }) to trigger workflow transitions
- GET /api/customers/{id}/verification
- POST /api/customers/{id}/verification/retry

Integration:
- Verification provider (async) — used by sendVerificationRequest and checkVerificationResult function.
- Notification service — used by notifySupport, scheduleDeactivation processors.

## Security & Permissions
- Authentication via OAuth2 / OpenID Connect.
- Role-based access control (RBAC) for Admin, Support, Customer.
- Input validation server-side and client-side; sanitize any user-provided metadata.
- Sensitive data handling: phone/email stored encrypted at rest if required by org policies.

## Non-functional Requirements (NFRs)
- Performance: Customer list API should support 1000 concurrent users; typical list page load < 500ms for cached queries.
- Availability: 99.9% uptime SLA for UI and API.
- Accessibility: WCAG 2.1 AA compliance for key screens.
- Responsiveness: Works on desktop, tablet, and mobile breakpoints.

## Notifications & UX
- Email notifications for verification outcomes, termination confirmations.
- In-app toasts for success/failure of actions, and modals for destructive actions (Terminate).
- Confirmation steps for Suspend/Terminate with reason capture.

## Acceptance Criteria
- Admin can create a Customer and see it in Customer List with status initial_state.
- Starting onboarding moves customer to onboarding and triggers verification request (mock provider accepted during tests).
- Verification success auto-transitions the customer to active and grants initial access (processor mocked in tests).
- Admin can suspend and reinstate a customer and see status changes reflected in UI and audit logs.
- Role-based restrictions prevent Support/Customer roles from performing Admin-only actions.

## UX Examples / Wireframe Notes
- Use a left navigation with sections: Dashboard, Customers, Verifications, Logs, Settings.
- Customer Detail page uses a two-column layout: left column summary + actions, right column tabbed content.
- Use status badges with consistent colors: initial_state (grey), onboarding (blue), verification_pending (orange), verified (teal), active (green), suspended (yellow), terminated (red).

## Open Questions
- Do we require customer-facing self-service signup (public registration) or only admin-created customers?
- Are documents required for verification? If yes, add uploads to onboarding.

---
Generated on: 2025-12-12
