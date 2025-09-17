Thanks for the clarification! Let's refine the entities and workflows based on your input.

### Updated Entities
1. **HN Item**: Represents a single Hacker News item, including attributes like title, URL, author, score, and parent ID.
2. **User**: Represents the user interacting with the service, responsible for triggering actions.
3. **Search Query**: Represents the criteria used to search for HN items, including filters and joins.

### Workflows
1. **Data Pull Workflow**: Triggers the process of pulling data from the Firebase HN API.
2. **Single Item POST Workflow**: Handles posting a single HN item to your service.
3. **Array POST Workflow**: Manages the posting of an array of HN items.
4. **Bulk Upload Workflow**: Facilitates the import of HN items from a JSON file, breaking it down into individual HN items.
5. **Search Workflow**: Allows users to search for HN items based on specified queries, including parent hierarchy joins. This workflow can be designed to operate asynchronously, meaning that the search action can be processed without blocking the user interface, allowing users to continue interacting with the service while the search is being completed.

Does this updated structure align with your vision? Would you like to explore any specific aspects further?