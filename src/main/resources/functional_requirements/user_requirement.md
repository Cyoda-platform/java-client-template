# Task Management System

Build a comprehensive task management system that allows users to create, assign, and track tasks through their lifecycle.

## Core Entities

### Task
A task represents a unit of work that needs to be completed. Tasks have:
- Basic information (title, description, priority)
- Assignment details (assignee user)
- Scheduling (due date, estimated hours)
- Progress tracking through workflow states

### User
A user represents a person who can be assigned tasks and manage the system. Users have:
- Profile information (name, email)
- Role-based permissions
- Status management (active/inactive)

## Key Workflows

### Task Lifecycle
Tasks progress through states: Created → Assigned → In Progress → Review → Completed
- Automatic assignment based on priority and user availability
- Progress tracking with time logging
- Review process for quality assurance
- Completion with final validation

### User Management
Users have a simple lifecycle: Registered → Active → Inactive
- Registration with email validation
- Activation process
- Deactivation for inactive users

## API Requirements

### Task Management
- Create new tasks with validation
- Assign tasks to users
- Update task progress and status
- Complete tasks with review
- List and filter tasks by various criteria

### User Management
- Register new users
- Activate/deactivate user accounts
- Update user profiles
- List users with filtering

## Business Rules
- Tasks must have a title and priority
- Only active users can be assigned tasks
- High priority tasks should be auto-assigned
- Tasks in review require validation before completion
- Users can only be deactivated if they have no active tasks