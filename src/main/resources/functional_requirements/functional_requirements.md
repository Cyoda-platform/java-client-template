# hello-greeting — Functional Requirements

Purpose
-------
Tiny demo showing a Greeting entity flowing through a single workflow and producing a printed greeting.

Entities
--------
- Greeting
  - timestamp: ISO-8601 string (e.g. 2026-05-03T09:30:00)
  - message: string

Workflow
--------
- Name: greeting_workflow
- States: START (initial), MORNING, AFTERNOON, PRINTED
- Processors:
  - InitializeGreetingProcessor: ensure timestamp exists (set to current time if missing)
  - SetMorningMessageProcessor: set message to "Good morning"
  - SetAfternoonMessageProcessor: set message to "Good afternoon"
  - PrintGreetingProcessor: print message to console
- Criteria:
  - IsMorningCriterion: timestamp hour < 12
  - IsAfternoonCriterion: timestamp hour >= 12

Flow
----
1. Greeting starts in START.
2. InitializeGreetingProcessor runs.
3. If IsMorningCriterion -> MORNING; else -> AFTERNOON.
4. Entering MORNING runs SetMorningMessageProcessor, then transition to PRINTED.
5. Entering AFTERNOON runs SetAfternoonMessageProcessor, then transition to PRINTED.
6. PRINTED runs PrintGreetingProcessor to log the final message.

Run
---
A simple main method should create a Greeting (timestamp optional), execute the workflow, and produce either "Good morning" or "Good afternoon" on stdout.
