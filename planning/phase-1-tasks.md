# Phase 1: Project Scaffolding & Boilerplate

This phase establishes the foundational structure of our Dropwizard application using Maven.

---

### Task 1: Generate Dropwizard Project Structure 🔄 PENDING

*   **Description:** Use the official Dropwizard Maven archetype to generate a new, clean project. This creates the standard directory structure, a default `pom.xml`, and the main application and configuration classes.
*   **Deliverables:**
    *   A new Maven project in the `restock-radar` directory.
    *   `pom.xml` file with default Dropwizard dependencies.
    *   `src/main/java/com/radar/stock/RestockRadarApplication.java`
    *   `src/main/java/com/radar/stock/RestockRadarConfiguration.java`
    *   `config.yml` file.

---

### Task 2: Add Project Dependencies 🔄 PENDING

*   **Description:** Update the `pom.xml` to include the specific libraries we'll need for this project, such as the Jakarta Mail API for sending emails.
*   **Deliverables:**
    *   Modified `pom.xml` with the `com.sun.mail:jakarta.mail` dependency added.

---

### Task 3: Create a "Hello World" Command 🔄 PENDING

*   **Description:** Create a basic Dropwizard `Command` class. This class will serve as the entry point for our stock-checking logic. For now, it will simply print a message to the console to confirm it's working.
*   **Deliverables:**
    *   New file: `src/main/java/com/radar/stock/commands/CheckStockCommand.java`.
    *   The `CheckStockCommand` class will extend `io.dropwizard.cli.Command`.
    *   The `run` method will contain a `System.out.println("Stock checker command is running...");` statement.

---

### Task 4: Register and Verify the Command 🔄 PENDING

*   **Description:** Register the new `CheckStockCommand` with the main application so Dropwizard knows it exists. Then, we will package the application into a JAR and run it from the command line to verify that our setup is correct.
*   **Deliverables:**
    *   Modified `RestockRadarApplication.java` to add the `CheckStockCommand` to its bootstrap sequence.
    *   A successful run of `mvn package` to create the executable JAR.
    *   Successful execution of `java -jar target/restock-radar-1.0-SNAPSHOT.jar check-stock` which prints the "Hello World" message. 