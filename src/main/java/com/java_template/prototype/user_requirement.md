# Requirement: Minimal e-commerce OMS backend on Cyoda for Lovable UI

- Create a minimal e-commerce OMS backend on Cyoda that powers the Lovable UI.
- Use Cyoda entities + workflows with the exact state names the UI expects.
- Must align with Lovable UI (entities & workflows): Product, Cart, Order, User, Address; Cart: NEW → ACTIVE → CHECKING_OUT → CONVERTED Order: WAITING_TO_FULFILL → PICKING → SENT User Identification: ANON → IDENTIFIED

## Entities (must be provided)
- Product
- Cart
- Order
- User
- Address

## Workflows / State machines (exact state names)
- Cart: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- Order: WAITING_TO_FULFILL → PICKING → SENT
- User Identification: ANON → IDENTIFIED

## Constraints / Notes
- Use Cyoda entities and Cyoda workflows.
- State names must match the exact strings the Lovable UI expects (as listed above).
- The backend must be minimal but sufficient to power the Lovable UI, following the entity and workflow definitions exactly.