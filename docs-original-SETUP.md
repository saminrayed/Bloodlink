# Complete Setup Guide

## 1. Install software

- JDK 21
- Maven 3.9+
- MySQL Server 8+
- IntelliJ IDEA, Antigravity/VS Code, or another Java IDE
- Scene Builder is optional for visually editing FXML

Verify:

```bash
java -version
javac -version
mvn -version
mysql --version
```

Both `java` and `javac` must report version 21.

## 2. Open the project

Open the folder containing `pom.xml`, not the `src` folder. Let the IDE import it as a Maven project.

Set Project SDK and Maven runner JDK to 21. The project is non-modular and intentionally has no `module-info.java`; this avoids the common “module not found” mismatch when JavaFX is run through Maven.

## 3. Configure MySQL credentials

Set these in the terminal before running Maven, or add them to the IDE run configuration:

```text
DB_URL=jdbc:mysql://localhost:3306/bloodlink_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_mysql_password
```

`.env.example` is documentation only. Standard Maven does not automatically load `.env`.

## 4. Initialize database

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.bloodlink.util.DatabaseSetup
```

Alternative shell scripts:

```bash
./scripts/setup-db.sh
```

```cmd
scripts\setup-db.cmd
```

## 5. Build and test

```bash
mvn clean test
mvn clean package
```

## 6. Run

```bash
mvn javafx:run
```

or:

```bash
./scripts/run.sh
```

```cmd
scripts\run.cmd
```

## 7. Scene Builder

Open files from `src/main/resources/com/bloodlink/view/`. Do not rename `fx:id` values or `onAction` methods unless you update the matching controller fields/methods.

## Common errors

### `release version 21 not supported`
Maven is using an older JDK. Point `JAVA_HOME`, the IDE Project SDK, and Maven runner to JDK 21.

### `mvn is not recognized`
Install Maven 3.9+ and add its `bin` directory to `PATH`.

### `Communications link failure`
Start MySQL and verify host, port, username, password, and firewall.

### `Access denied for user`
Use a MySQL account that can create `bloodlink_db`, or create the database manually and grant privileges.

### `Unknown database bloodlink_db`
Run `DatabaseSetup`, or set `DB_AUTO_INIT=true` for the first launch.

### `Location is not set` or FXML load failure
Run from the Maven project root. Confirm FXML files remain under `src/main/resources/com/bloodlink/view/`.

### Duplicate demo data concern
User records are upserted. Demo request rows are inserted only when no request with a `[DEMO]` note exists for the demo requester.
