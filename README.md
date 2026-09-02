# BloodLink

*An emergency blood response platform that reduces the time required to locate eligible blood donors during critical medical situations through intelligent donor prioritization, eligibility assessment, and real-time emergency coordination.*

**Emergency Blood Response & Donor Coordination Platform**  
CSE 4402 — Visual Programming Lab, Islamic University of Technology

BloodLink is a complete JavaFX desktop application for coordinating emergency blood requests with eligible donors. It implements role-based authentication, donor eligibility and availability, ranked donor matching, a full request lifecycle, in-app notifications, profile management, achievement badges, administrator controls, analytics, and audit history.

## Required versions

- JDK 21
- Maven 3.9+
- MySQL 8+
- JavaFX 21.0.9 (downloaded by Maven)

The Maven build enforces JDK 21. This project does **not** use Java 17.

## Fast setup

1. Install JDK 21, Maven 3.9+, and MySQL 8+.
2. Set environment variables. Copy `.env.example` as a reference; Maven does not automatically read `.env`, so set the values in your terminal or IDE run configuration.
3. Initialize the schema and demo data:

   ```bash
   mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.bloodlink.util.DatabaseSetup
   ```

4. Run tests:

   ```bash
   mvn test
   ```

5. Start the application:

   ```bash
   mvn javafx:run
   ```

Windows shortcuts are available in `scripts/*.cmd`; Linux/macOS shortcuts are in `scripts/*.sh`.

## Environment variables

```text
DB_URL=jdbc:mysql://localhost:3306/bloodlink_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_mysql_password
DB_AUTO_INIT=false
AUTO_REFRESH_SECONDS=10
```

Set `DB_AUTO_INIT=true` only when you want the application itself to create the schema and seed demo records during startup. For normal use, run the setup command once and leave it `false`.

There is no embedded-database fallback: if MySQL is unreachable, the application fails with a clear connection error rather than silently switching to a local file database. Fix the MySQL connection (service running, environment variables correct for this session) rather than expecting the app to route around it.

### PowerShell example

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/bloodlink_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
mvn javafx:run
```

### Bash example

```bash
export DB_URL='jdbc:mysql://localhost:3306/bloodlink_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='root'
export DB_PASSWORD='your_mysql_password'
mvn javafx:run
```

## Demo accounts

The database setup utility creates these accounts:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@bloodlink.local` | `Admin@123` |
| Donor | `donor.opos@bloodlink.local` | `Donor@123` |
| Requester | `requester@bloodlink.local` | `Request@123` |

Change or remove demo credentials before using the application outside a classroom demonstration.

## Architecture

```text
FXML/CSS View
      ↓
Controller
      ↓
Service / Business Rules
      ↓
DAO / Prepared SQL
      ↓
MySQL
```

- `controller`: screen state and event handlers
- `service`: validation, authentication, eligibility, matching, lifecycle rules, authorization
- `dao`: SQL, transactions, persistence mapping
- `model`: domain entities and enums
- `util`: configuration, sessions, navigation, password hashing, dialogs, database bootstrap, background task execution
- `resources/view`: FXML screens
- `resources/css`: shared design system
- `resources/sql`: runtime schema copy

## Implemented feature summary

### Donor
- Register with health and blood-group details
- Admin approval gate
- Login with BCrypt password verification
- Availability toggle: Available, Busy, Out of Town, Medical Hold
- Age, weight, and 56-day eligibility engine
- Matching notifications with accept/decline flow
- Donation history, cooldown indicator, and badge tier
- Distance from registered district to each matched request's hospital
- Profile and password update

### Requester
- Submit emergency request with blood group, units, urgency, hospital, district, deadline, and notes
- Searchable hospital picker backed by a curated hospital directory with real coordinates
- Automatically run ranked, distance-aware matching
- View matched donors, distance, and match reasons
- Re-run matching after declined/no-match states
- Track lifecycle history
- Mark accepted requests fulfilled or cancel an open request
- Profile, password, and notification management

### Administrator
- Summary cards and live analytics
- Blood-group BarChart, monthly LineChart, status PieChart
- Blood-group demand-versus-availability table
- Search users and requests
- Approve, suspend, activate, and reset user passwords (server-side admin verification, not just UI gating)
- Escalate or close requests (server-side admin verification, not just UI gating)
- View audit events

## Matching model

Only approved, active, `AVAILABLE`, compatible, and currently eligible donors can be selected. Candidates receive a score from:

- compatible blood group baseline
- exact blood group bonus
- same-district bonus
- distance from the donor's registered district to the request's hospital, where known
- completed cooldown / time since donation
- verified donation experience
- request urgency

The highest eight candidates are saved and notified. Matching is deterministic for equal data, with donor name used as the final ordering rule.

## Resource locations

- FXML: `src/main/resources/com/bloodlink/view/`
- CSS: `src/main/resources/com/bloodlink/css/theme.css`
- SQL: `src/main/resources/com/bloodlink/sql/schema.sql`
- Config: `src/main/resources/com/bloodlink/config/application.properties`

FXML is loaded through:

```java
Main.class.getResource("/com/bloodlink/view/" + fxml)
```

Every FXML file loads the stylesheet through:

```xml
stylesheets="@../css/theme.css"
```

No external icon pack or custom font is required. The current interface uses Unicode symbols and system font fallbacks, so missing asset files cannot break startup.

## Full documentation

- `docs/PROJECT_ANALYSIS.md`
- `docs/ARCHITECTURE.md`
- `docs/DATABASE.md`
- `docs/FILE_CATALOG.md`
- `docs/RESOURCE_MAP.md`
- `docs/SETUP.md`
- `docs/TEST_PLAN.md`
- `docs/COMPLETION_CHECKLIST.md`

The originally uploaded proposal, README, Maven file, and `.gitignore` are preserved under `docs/reference/` for traceability.
