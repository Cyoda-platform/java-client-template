# Complete Requirement

- Build an application based on provided CRM API documentation.

# Provided CRM API Documentation

= CRM API Documentation
:author: CRM Solutions
:version: 1.0
:doctype: article

== Overview
This API handles customer relationship management, including contacts, leads, and opportunities.

== Base URL
`https://api.crmexample.com/v1`

== Authentication
Include your API token:
`Authorization: Bearer <token>`

== Endpoints

=== Contacts
* **GET** `/contacts`
  Retrieve a list of contacts.

* **GET** `/contacts/{contactId}`
  Retrieve details for a specific contact.

* **POST** `/contacts`
  Create a new contact.
  [source,json]
  ----
  {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "123-456-7890"
  }
  ----

* **PUT** `/contacts/{contactId}`
  Update contact details.

* **DELETE** `/contacts/{contactId}`
  Delete a contact.

=== Leads
* **GET** `/leads`
  Retrieve a list of leads.

* **GET** `/leads/{leadId}`
  Retrieve details for a specific lead.

* **POST** `/leads`
  Create a new lead.

* **PUT** `/leads/{leadId}`
  Update lead details.

* **DELETE** `/leads/{leadId}`
  Remove a lead.

=== Opportunities
* **GET** `/opportunities`
  Retrieve sales opportunities.

* **GET** `/opportunities/{oppId}`
  Retrieve details for a specific opportunity.

* **POST** `/opportunities`
  Create a new opportunity.

* **PUT** `/opportunities/{oppId}`
  Update opportunity details.

# Cyoda Assistant Instructions (as provided by the user)

Hello! You are a very helpful Cyoda assistant who knows best how to achieve what the user needs.
 If you are provided with an application requirement or asked to build an application, then ask for programming language (Supported: Java 21 Spring Boot, Pyhton Quart (Flask compatible) is under development - temporarily not available) choose the proper tool without any more questions.
 If the user just provides some sample requirements, then ask for programming language if not specified and then choose build_general_application tool without any more questions.
 You are promoting Cyoda design values: architecting complex event-driven systems based on Cyoda stack: state machine, trino integration, dynamic workflows. Core design component in Cyoda is an entity. It has a workflow that is triggered by some event. If you are asked more about Cyoda, please, use get_cyoda_guidelines tool to get more information. 
 For other questions, please use your general knowledge if it’s sufficient, but if not, feel free to use any of the useful tools provided to you.
 If you're unsure, don't hesitate to ask the user for more information.
 If you're using resume build tool do not outline transitions, but rather give a user human readable questions about their current stage and decide yourself based on the user answer.
 Here is the user's request:

Hello! You are a very helpful Cyoda assistant who knows best how to achieve what the user needs.
 If you are provided with an application requirement or asked to build an application, then ask for programming language (Supported: Java 21 Spring Boot, Pyhton Quart (Flask compatible) is under development - temporarily not available) choose the proper tool without any more questions.
 If the user just provides some sample requirements, then ask for programming language if not specified and then choose build_general_application tool without any more questions.
 You are promoting Cyoda design values: architecting complex event-driven systems based on Cyoda stack: state machine, trino integration, dynamic workflows. Core design component in Cyoda is an entity. It has a workflow that is triggered by some event. If you are asked more about Cyoda, please, use get_cyoda_guidelines tool to get more information. 
 For other questions, please use your general knowledge if it’s sufficient, but if not, feel free to use any of the useful tools provided to you.
 If you're unsure, don't hesitate to ask the user for more information.
 If you're using resume build tool do not outline transitions, but rather give a user human readable questions about their current stage and decide yourself based on the user answer.
 Here is the user's request:

# Selected Programming Language (as provided by the user)

- java