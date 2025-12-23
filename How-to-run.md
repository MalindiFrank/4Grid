---

## **How to Run Locally**

### **Prerequisites**

Ensure following are installed on your local machine:

* **Java 17+**
* **Maven 3.8+**
* **ActiveMQ (Classic)**

> The system was developed and tested using Java and Maven on a Linux environment, but it should run on any OS that supports Java and ActiveMQ.

---

### **1. Clone the Repository**

```bash
git clone https://github.com/MalindiFrank/4Grid.git
cd 4Grid
```

---

### **2. Start ActiveMQ**

4Grid relies on ActiveMQ for asynchronous messaging between services.

Start ActiveMQ using your local installation:

```bash
activemq start
```

By default, ActiveMQ runs on:

* **Broker URL:** `tcp://localhost:61616`
* **Web Console:** `http://localhost:8161`

Ensure the broker is running before starting any services.

---

### **3. Build Shared Components**

The `4grid-common` module must be built first, as it is shared across services.

```bash
cd 4grid-common
mvn clean install
```

---

### **4. Start Backend Services**

Each service runs as an independent Java process.
Open **separate terminal windows** for each service.

#### **Stage Service**

```bash
cd 4grid-stage-service
mvn clean compile exec:java
```

#### **Schedule Service**

```bash
cd 4grid-schedule-service
mvn clean compile exec:java
```

#### **Alert Service**

```bash
cd 4grid-alert-service
mvn clean compile exec:java
```

---

### **5. Start the Web Service**

```bash
cd 4grid-web-service
mvn clean compile exec:java
```

Once running, the web service will:

* Aggregate data from backend services
* Serve the web UI
* Expose frontend-facing APIs

---

### **6. Verify the System**

You can verify that the system is running by:

* Querying REST endpoints exposed by the services
* Triggering a stage change in `4grid-stage-service`
* Observing:

  * Messages published to ActiveMQ topics
  * Alerts consumed by `4grid-alert-service`
  * Updated state reflected in the web interface

---

### **Startup Order (Recommended)**

1. ActiveMQ
2. `4grid-common`
3. `4grid-stage-service`
4. `4grid-schedule-service`
5. `4grid-alert-service`
6. `4grid-web-service`

---

### **Notes**

* Services are intentionally run independently to reflect real-world distributed systems.
* No containers or orchestration tools are required for local development.
* Configuration values (ports, broker URLs) can be adjusted via each service’s configuration files if needed.

---
