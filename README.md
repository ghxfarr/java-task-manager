# java-task-manager
Task Manager is a Java-based application with a user-friendly Swing GUI and MySQL database integration. It enables users to add, update, delete, and manage tasks effectively. With support for scheduling start and end dates, data persistence, and input validation, it is designed to improve productivity and task organization.

## ✨ Main Features

- **Complete Task Management**
Add, update, and delete tasks easily.

- **Database Support**
All data is stored permanently in **MySQL** using `mysql-connector-j`.

- **Stuling Arrangement**
Set the start date and finish date for each task.

- **Simple UI**
Easy-to-use Swing-based interface.

- **Notification & Validation**
Warning if there is an input error or certain restrictions are not met.

---

## 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/Swing-007396?style=for-the-badge&logo=java&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![JDBC](https://img.shields.io/badge/JDBC-003B57?style=for-the-badge&logo=java&logoColor=white)
![MySQL Connector J](https://img.shields.io/badge/MySQL%20Connector%2FJ-4479A1?style=for-the-badge&logo=databricks&logoColor=white)

- **Java SE 22** → main language for application logic  
- **Swing (Java Swing GUI)** → Java default framework to create a graphical interface 
- **MySQL** → relational database to store task data  
- **MySQL Connector/J (JDBC Driver)** → library to connect Java with MySQL  
- **JDBC (Java Database Connectivity)** → API standard for interaction with database

---

## ⚙️ How to Install

1. **Clone this repository**:
```
git clone https://github.com/ghxfarr/TaskManager.git
cd TaskManager
```
2. **Make sure you have installed**:
   - Java JDK 22 or the latest version
   - MySQL Server
   - MySQL Connector J (already available in the project folder as mysql-connector-j-9.3.0.jar)
3. **Create MySQL database**:
```
CREATE DATABASE taskmanager_db;
USE taskmanager_db;

CREATE TABLE tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATETIME,
    end_date DATETIME,
    status VARCHAR(50)
);
```
4. **Set the database connection configuration in the DatabaseConnection.java file**:
```
private static final String URL = "jdbc:mysql://localhost:3306/taskmanager_db";
private static final String USER = "(Your Username)";
private static final String PASSWORD = "(Your Password)";
```
5. **Project compilation**:
```
javac -cp ".:mysql-connector-j-9.3.0.jar" TaskManager.java DatabaseConnection.java
```

---

## ▶️ How to Use

1. **Run the program**:
```
java -cp ".:mysql-connector-j-9.3.0.jar" TaskManager
```
2. **Use the application for**:
   - Adding a new task
   - Updating task details
   - Delete unnecessary tasks
   - See the list of all tasks
3. **Input Example**:
   - Title: ```Learn Java```
   - Description: ```Complete the Task Manager project```
   - StartDate: ```22/08/2025 10:00```
   - EndDate: ```22/08/2025 12:00```

---

## Used By

This project is used by the following companies:

- **TITIK SIBER COMMUNITY**
