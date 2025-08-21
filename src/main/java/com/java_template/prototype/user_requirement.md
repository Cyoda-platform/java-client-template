# 15 Filtered Pet Search Application with Transformation

Application that retrieves pet details using multiple parameters and includes a transformation step. The application should:

- API https://app.swaggerhub.com/apis/WinBeyond/PetstorePetstore/1.0.0#/pet/findPetsByStatus
- Data Ingestion: Fetch pet details using parameters such as species (e.g., "dog"), status (e.g., "available"), and category ID.
- Data Transformation: Convert the received data into a user-friendly format, including renaming fields (e.g., converting "petName" to "Name") and incorporating additional attributes such as availability status.
- Data Display: Present the list of pets that match the search criteria along with their transformed information.
- User Interaction: Allow users to customize their search with the specified parameters.
- Notifications: Alert users if no pets match the search criteria.
- Data ingestion and transformation should be conducted on-demand whenever the user adjusts their search parameters.