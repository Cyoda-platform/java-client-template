# Functional Requirements: Minimal 1:1 Anonymous Chat

Purpose:
- Provide a minimal human-to-human chat application for demo and learning.

Scope:
- 1:1 chat only (no groups/rooms).
- Anonymous users — no registration or persistent user accounts.
- Messages are ephemeral and stored only in-memory for the lifetime of the server process.
- Real-time communication using WebSocket.
- Simple HTML/JS client served from the backend.

Core Features:
- Establish WebSocket connection between two peers via a shared room code.
- Send/receive text messages in real-time.
- Show connection/typing indicators.
- No message persistence beyond server uptime.

Non-Functional Requirements:
- Easy to run locally with minimal dependencies.
- Secure WebSocket (wss) support when deployed behind TLS.
- Lightweight resource usage.

Deliverables:
- Java backend (Spring Boot) with WebSocket endpoint and in-memory message store.
- Static frontend (HTML/CSS/JS) served from backend for demo.
- README with local run instructions and branch details.

Assumptions:
- TLS and production hardening are out of scope for this minimal demo.
- No file attachments, emoji, or rich text.
