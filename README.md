# Disaster Management System

A Java Swing desktop application (CGB1201 - Java Programming) that detects disasters, assesses severity, sends alerts, manages resources and tracks response and recovery.

## Modules
1. Detection and Data Collection: enter simulated sensor data, stored in an in-memory database
2. Severity Assessment: score out of 100 (intensity 40 + casualties 30 + area 30), PriorityQueue ordering
3. Alert and Notification: Notifier interface for authorities, responders and the public
4. Resource Management: rescue team and ambulance stock, with a custom ShortageException
5. Response and Recovery: status tracking (Reported, Assigned, In Progress, Resolved) and final report

## Technologies Used
- Java (Swing GUI)
- Collections: ArrayList, PriorityQueue, LinkedHashMap
- OOP: classes, interface, enum, custom exception

## How to Run
1. Install JDK 8 or above
2. Open a terminal in the project folder
3. Compile: `javac DisasterManagementSystem.java`
4. Run: `java DisasterManagementSystem`

## How to Use
1. Open "1. Detection" and click "Load Sample Data"
2. Open "2. Severity" and click "Assess & Prioritize"
3. Open "5. Response" and click "Process All"
4. Check "3. Alerts" and "4. Resources", then click "Generate Final Report"

## Author
Sri Nivas S, Batch 10
