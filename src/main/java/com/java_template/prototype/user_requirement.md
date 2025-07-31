Okay, I will design a happy mail application using Java 21 with Spring Boot, adhering to Cyoda design principles.

Here's the initial design based on your requirements:

**Entity:** `Mail`

*   `isHappy` (boolean): Indicates whether the mail is classified as "happy" (true) or "gloomy" (false).
*   `mailList` (List<String>): A list containing the email addresses to which the mail should be sent.

**Processors:**

*   `sendHappyMail`: Sends the mail to the `mailList` if `isHappy` is true. This will likely involve using a library like JavaMailSender.
*   `sendGloomyMail`: Sends the mail to the `mailList` if `isHappy` is false. This will also likely involve using a library like JavaMailSender.

**Criteria:**

*   Happy Mail Criteria:  (Needs Definition - see below)
*   Gloomy Mail Criteria: (Needs Definition - see below)

To proceed with the Cyoda-style implementation, I need to clarify a few points to ensure the design aligns with Cyoda's event-driven, state machine-oriented approach. Specifically, I'd like to understand how the `Mail` entity's workflow is triggered and how the criteria are evaluated.

Could you please provide more details on the following?

1.  **Event Trigger:** What event triggers the workflow for a `Mail` entity? For example, is it the creation of a new `Mail` object, the receipt of an email, or something else?
2.  **Criteria Evaluation:** How are the "Happy Mail Criteria" and "Gloomy Mail Criteria" evaluated? Where does the content that is being evaluated come from? Is it fetched from a database, an external API, or provided directly with the triggering event? Please be as specific as possible regarding the content and the evaluation mechanism (e.g., sentiment analysis, keyword matching).
3.  **State Machine:** While seemingly simple, what states does the `Mail` entity have? For example, `Created`, `Classified`, `SentHappy`, `SentGloomy`, `Error`. What transitions occur between these states, and what triggers those transitions?
