= Task Management API Documentation
:author: Project Management Team
:version: 1.0
:doctype: article

# Overview
This API is built for managing projects and tasks in a collaborative work environment.

# Base URL
`https://api.taskmanager.com/v1`

# Authentication
Pass your API token in the header:
`Authorization: Bearer <token>`

# Endpoints

## Projects
- **GET** `/projects`  
  Retrieve a list of projects.

- **GET** `/projects/{projectId}`  
  Retrieve details for a specific project.

- **POST** `/projects`  
  Create a new project.
  ```json
  {
    "name": "Website Redesign",
    "description": "Redesign the corporate website",
    "startDate": "2025-03-01",
    "endDate": "2025-04-01"
  }
  ```

- **PUT** `/projects/{projectId}`  
  Update project details.

- **DELETE** `/projects/{projectId}`  
  Delete a project.

## Tasks
- **GET** `/projects/{projectId}/tasks`  
  Retrieve tasks under a project.

- **GET** `/tasks/{taskId}`  
  Retrieve details of a specific task.

- **POST** `/projects/{projectId}/tasks`  
  Create a new task.
  ```json
  {
    "title": "Design Homepage",
    "description": "Create a modern homepage design",
    "status": "pending",
    "dueDate": "2025-03-15"
  }
  ```

- **PUT** `/tasks/{taskId}`  
  Update a task.

- **PATCH** `/tasks/{taskId}`  
  Partially update a task (e.g., update status).

- **DELETE** `/tasks/{taskId}`  
  Delete a task.