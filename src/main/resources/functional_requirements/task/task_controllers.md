# Task Controller

## Overview
TaskController provides REST API endpoints for managing tasks throughout their lifecycle.

## Endpoints

### POST /api/tasks
Create a new task.

**Request Example:**
```json
{
  "taskId": "TASK-001",
  "title": "Implement user authentication",
  "description": "Add JWT-based authentication to the API",
  "priority": "HIGH",
  "assigneeId": "USER-123",
  "dueDate": "2024-01-15T10:00:00",
  "estimatedHours": 8
}
```

**Response Example:**
```json
{
  "entity": {
    "taskId": "TASK-001",
    "title": "Implement user authentication",
    "description": "Add JWT-based authentication to the API",
    "priority": "HIGH",
    "assigneeId": "USER-123",
    "dueDate": "2024-01-15T10:00:00",
    "estimatedHours": 8,
    "actualHours": null,
    "createdAt": "2024-01-10T09:00:00",
    "updatedAt": "2024-01-10T09:00:00"
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "created",
    "version": 1
  }
}
```

### PUT /api/tasks/{taskId}
Update a task with optional state transition.

**Request Example:**
```json
{
  "title": "Implement user authentication (Updated)",
  "description": "Add JWT-based authentication with refresh tokens",
  "assigneeId": "USER-456",
  "transition": "assign_task"
}
```

**Response Example:**
```json
{
  "entity": {
    "taskId": "TASK-001",
    "title": "Implement user authentication (Updated)",
    "description": "Add JWT-based authentication with refresh tokens",
    "priority": "HIGH",
    "assigneeId": "USER-456",
    "dueDate": "2024-01-15T10:00:00",
    "estimatedHours": 8,
    "actualHours": null,
    "createdAt": "2024-01-10T09:00:00",
    "updatedAt": "2024-01-10T11:30:00"
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "assigned",
    "version": 2
  }
}
```

### GET /api/tasks/{taskId}
Get a specific task by ID.

**Response Example:**
```json
{
  "entity": {
    "taskId": "TASK-001",
    "title": "Implement user authentication",
    "description": "Add JWT-based authentication to the API",
    "priority": "HIGH",
    "assigneeId": "USER-123",
    "dueDate": "2024-01-15T10:00:00",
    "estimatedHours": 8,
    "actualHours": null,
    "createdAt": "2024-01-10T09:00:00",
    "updatedAt": "2024-01-10T09:00:00"
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "created",
    "version": 1
  }
}
```

### GET /api/tasks
List tasks with optional filtering.

**Query Parameters:**
- assigneeId: Filter by assigned user
- state: Filter by task state
- priority: Filter by priority level

**Response Example:**
```json
[
  {
    "entity": {
      "taskId": "TASK-001",
      "title": "Implement user authentication",
      "priority": "HIGH",
      "assigneeId": "USER-123",
      "dueDate": "2024-01-15T10:00:00"
    },
    "meta": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "state": "assigned",
      "version": 1
    }
  }
]
```
