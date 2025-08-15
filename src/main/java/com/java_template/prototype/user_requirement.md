# Complete Requirement for Event Management Application

- **Purpose:**  
  Build an application for managing events, bookings, and ticketing for conferences, concerts, and other events.

- **Based on:**  
  The provided Event Booking API documentation.

- **API Documentation Details:**

  ## Overview
  - The API manages events, bookings, and ticketing.
  - Applicable for conferences, concerts, and other event types.

  ## Base URL
  - `https://api.eventbooking.com/v1`

  ## Authentication
  - Use OAuth2 or an API key in the header.
  - Header example: `Authorization: Bearer <token>`

  ## Endpoints

  ### Events
  - `GET /events`  
    Retrieve a list of events.  
    Query Parameters:  
      - location  
      - date  
      - category

  - `GET /events/{eventId}`  
    Retrieve details for a specific event.

  - `POST /events`  
    Create a new event.  
    Example JSON body:  
    ```json
    {
      "title": "Tech Conference 2025",
      "description": "Annual technology conference",
      "date": "2025-06-15",
      "location": "Convention Center"
    }
    ```

  - `PUT /events/{eventId}`  
    Update event details.

  - `DELETE /events/{eventId}`  
    Cancel an event.

  ### Bookings
  - `GET /bookings`  
    Retrieve all bookings.

  - `GET /bookings/{bookingId}`  
    Retrieve details for a specific booking.

  - `POST /events/{eventId}/bookings`  
    Create a booking for an event.  
    Example JSON body:  
    ```json
    {
      "userId": "user123",
      "tickets": 2,
      "bookingDate": "2025-05-01"
    }
    ```

  - `DELETE /bookings/{bookingId}`  
    Cancel a booking.

- **Additional Notes:**  
  - The application must support managing events, bookings, and ticketing workflows fully aligned to the API capabilities.
  - Authentication must be handled via OAuth2 or API key as specified.
  - The application should support filtering, creation, update, retrieval, and cancellation operations for both events and bookings.