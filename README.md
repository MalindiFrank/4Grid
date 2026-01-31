---

# **4Grid**

### Distributed Load Shedding System

---

## **Overview**

**4Grid** is a distributed, service-oriented backend system built in **Java** to manage load shedding stages, schedules, and alerts.

The system uses **REST APIs** and **asynchronous messaging** to coordinate state across independent services and expose grid information to a web interface.
This project emphasizes **system design**, **inter-service communication**, and **event-driven architecture**, rather than simple CRUD-based workflows.

---

## **Key Objectives**

* Model a real-world, distributed software system
* Demonstrate service ownership and responsibility boundaries
* Implement asynchronous event propagation using message queues
* Build loosely coupled services with clear communication contracts
* Reflect production-style engineering patterns

---

## **Architecture Overview**

4Grid follows a **distributed service architecture** where each service has a **single, well-defined responsibility**.

Services communicate using:

* **Synchronous REST calls** for request/response interactions
* **Asynchronous message queues/topics** for event-driven updates

This approach enables scalability, resilience, and independent service evolution.

---

## **System Architecture (Conceptual)**

```

```

---

## **Core Components**

### **stage-service**

* Maintains the **current load shedding stage**
* Acts as the **source of truth** for grid stage state
* Publishes stage change events to a message topic

---

### **schedule-service**

* Provides load shedding schedules per **province and area**
* Exposes schedules via REST APIs
* Can react to stage updates if required

---

### **alert-service**

* Subscribes to stage update events
* Generates alerts when load shedding stages change
* Demonstrates **event-driven processing**

---

### **web-service**

* Aggregates data from backend services
* Exposes a simplified API for the frontend
* Serves the web-based user interface

---

### **common**

* Shared **Data Transfer Objects (DTOs)**
* Messaging utilities and communication contracts
* Ensures consistency across services

---

## **Communication Model**

* **REST**

  * Used for synchronous request/response operations
  * Example: fetching schedules or current stage information

* **Message Queues / Topics (ActiveMQ)**

  * Used for asynchronous events such as stage changes
  * Enables loose coupling and non-blocking workflows

Services communicate exclusively through **well-defined contracts**, avoiding tight dependencies.

---

## **Design Principles**

* Loose coupling between services
* Clear ownership of data and responsibilities
* Event-driven state propagation
* Separation of concerns
* Scalability and resilience by design

This architecture mirrors distributed systems commonly found in enterprise and cloud-based environments.

---
