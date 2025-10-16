# Pet Store REST API Application

## Overview
Build a comprehensive REST API application for a pet store that manages pets, customers, and orders. The application follows the Cyoda workflow-driven architecture with proper entity lifecycle management.

## Core Entities

### 1. Pet Entity
- Manages pet inventory with details like name, category, breed, price, photos
- Workflow states: initial → available → pending → sold
- Supports pet reservation, sales, and cancellation workflows
- REST endpoints for CRUD operations, search by status/tags, and workflow transitions

### 2. Customer Entity
- Manages customer accounts with personal information, preferences, and loyalty data
- Workflow states: initial → active → inactive/suspended → deleted
- Supports customer lifecycle management and account status changes
- REST endpoints for CRUD operations, search, and account management

### 3. Order Entity
- Manages purchase orders linking customers to pets with pricing and fulfillment
- Workflow states: initial → placed → confirmed → preparing → shipped → delivered
- Supports order processing, payment, shipping, and return workflows
- REST endpoints for CRUD operations, customer/pet filtering, and order lifecycle

## Key Features
- Complete CRUD operations for all entities
- Workflow-driven business logic with proper state transitions
- Advanced search and filtering capabilities
- Historical data queries with point-in-time support
- Comprehensive order management from placement to delivery
- Customer account management with status controls
- Pet inventory management with availability tracking

## API Endpoints
- Pet endpoints: `/ui/pet/**` - 15+ endpoints for pet management
- Customer endpoints: `/ui/customer/**` - 16+ endpoints for customer management
- Order endpoints: `/ui/order/**` - 17+ endpoints for order management

## Business Logic
- Automatic pet reservation when orders are placed
- Customer validation for order placement
- Inventory tracking and availability management
- Order fulfillment workflow with shipping and delivery
- Customer loyalty and preference tracking
- Comprehensive audit trails and change history