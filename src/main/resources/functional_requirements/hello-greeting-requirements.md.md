# Hello Greeting — Functional Requirements

Purpose
- Tiny demo showing a Greeting entity moving through a simple workflow and ending with a printed message on the console.

Scope
- Very small Java Cyoda app (front-page demo).

Functional requirements
- One entity: Greeting
  - Fields: timestamp, message
- One workflow (JSON): HelloGreetingWorkflow
  - States: START, MORNING, AFTERNOON, PRINTED
  - Criteria: IsMorningCriterion (timestamp before 12:00), IsAfternoonCriterion (timestamp 12:00 or later)
  - Processors:
    - InitializeGreetingProcessor: ensure timestamp exists (system local time if absent)
    - SetMorningMessageProcessor: set message to "Good morning"
    - SetAfternoonMessageProcessor: set message to "Good afternoon"
    - PrintGreetingProcessor: print message to console/log

Behavior
- Greeting entities start in START.
- InitializeGreetingProcessor runs in START to ensure timestamp.
- Based on timestamp, transition to MORNING or AFTERNOON.
- Entering MORNING sets message to "Good morning"; entering AFTERNOON sets "Good afternoon".
- Both paths transition to PRINTED where PrintGreetingProcessor logs the message.

Demo mode
- Timestamp is set from system local time if not provided.

Expected output
- "Good morning" or "Good afternoon" printed to console depending on local time.
