# File-by-File Catalog

Every file below is complete; no file contains a placeholder method or unfinished event handler.

## Project root

| File | Purpose |
|---|---|
| `pom.xml` | Java 21 Maven build, JavaFX, MySQL, BCrypt, JUnit, compiler, test, run, and setup plugins. |
| `.gitignore` | Excludes Maven output, IDE files, logs, and private environment files. |
| `.env.example` | Safe template for database and refresh settings. |
| `README.md` | Primary setup, architecture, feature, account, and resource guide. |

## Database and scripts

| File | Purpose |
|---|---|
| `database/schema.sql` | Human-accessible copy of the normalized MySQL schema. |
| `scripts/setup-db.sh` | Linux/macOS database bootstrap command. |
| `scripts/setup-db.cmd` | Windows database bootstrap command. |
| `scripts/run.sh` | Linux/macOS JavaFX run command. |
| `scripts/run.cmd` | Windows JavaFX run command. |
| `scripts/verify-project.py` | XML, FXML/controller, resource, package, SQL-copy, and unfinished-marker verification. |

## Entry point

| File | Purpose | Dependencies/resources |
|---|---|---|
| `src/main/java/com/bloodlink/Main.java` | Starts JavaFX, installs the global exception handler, optionally initializes MySQL, opens login, and warns when the DB is unavailable. | `application.properties`, `SceneManager`, `DatabaseSetup` |

## Controllers

| File | Purpose | FXML |
|---|---|---|
| `controller/LoginController.java` | Validates login input, authenticates, and routes by role. | `login.fxml` |
| `controller/RegisterController.java` | Donor/requester form switching, validation, and registration. | `register.fxml` |
| `controller/DonorDashboardController.java` | Eligibility, availability, matches, responses, history, notifications, and profile. | `donor_dashboard.fxml` |
| `controller/RequesterDashboardController.java` | Request creation, matching, lifecycle actions/history, notifications, and profile. | `requester_dashboard.fxml` |
| `controller/AdminDashboardController.java` | Analytics, user/request administration, demand, audit, and search. | `admin_dashboard.fxml` |

## Models and enums

| File | Purpose |
|---|---|
| `model/User.java` | Shared abstract user identity and state. |
| `model/Donor.java` | Donor health, availability, donation count, and badge behavior. |
| `model/Requester.java` | Requester specialization. |
| `model/Admin.java` | Administrator specialization. |
| `model/RegistrationData.java` | Validated registration command object. |
| `model/BloodRequest.java` | Complete emergency request record. |
| `model/Notification.java` | Inbox notification record. |
| `model/DonationRecord.java` | Verified donation history row. |
| `model/MatchCandidate.java` | Ranked donor candidate created by matching. |
| `model/DonorMatchView.java` | Donor-facing request/match projection. |
| `model/AdminUserRow.java` | Administrator user-table projection. |
| `model/DemandRow.java` | Blood-group demand/availability projection. |
| `model/AuditEntry.java` | Audit-log projection. |
| `model/DashboardStats.java` | Administrator summary metrics. |
| `model/RequestStatusHistoryEntry.java` | Request lifecycle transition projection. |
| `model/Role.java` | DONOR, REQUESTER, ADMIN. |
| `model/AvailabilityStatus.java` | Donor availability choices. |
| `model/Urgency.java` | NORMAL, URGENT, CRITICAL plus score weight. |
| `model/RequestStatus.java` | Request lifecycle states. |
| `model/MatchStatus.java` | NOTIFIED, ACCEPTED, DECLINED, EXPIRED. |
| `model/BadgeTier.java` | Donation-count badge thresholds. |
| `model/BloodGroup.java` | ABO/Rh display and compatibility rules. |

## Services

| File | Purpose |
|---|---|
| `service/ServiceResult.java` | Consistent success/failure result wrapper for controllers. |
| `service/AuthService.java` | Registration/login validation and account-state rules. |
| `service/EligibilityService.java` | Age, weight, and 56-day cooldown engine. |
| `service/MatchingService.java` | Compatible donor filtering, scoring, sorting, and persistence. |
| `service/RequestService.java` | Request creation and lifecycle façade. |
| `service/DonorService.java` | Donor availability and health update validation. |
| `service/ProfileService.java` | Contact/profile changes and password verification/update. |
| `service/NotificationService.java` | Notification listing, count, and read operations. |
| `service/AdminService.java` | Protected user approval/state/password operations. |

## DAOs

| File | Purpose |
|---|---|
| `dao/UserDAO.java` | User lookup, role mapping, registration, profile/password updates, and account states. |
| `dao/DonorDAO.java` | Available donors, health/status updates, and donation history. |
| `dao/RequestDAO.java` | Request CRUD, matching, locking, responses, fulfillment, cancellation, history, and transitions. |
| `dao/NotificationDAO.java` | Transaction-aware notification creation and inbox queries. |
| `dao/AdminDAO.java` | Dashboard metrics, charts, demand table, user/request search, and audit query. |
| `dao/AuditDAO.java` | Shared audit insert helper. |

## Utilities

| File | Purpose |
|---|---|
| `util/AppConfig.java` | Loads properties and resolves `${ENV:default}` expressions. |
| `util/DBConnection.java` | Creates JDBC connections and tests availability. |
| `util/DatabaseSetup.java` | Creates database/schema and idempotently seeds demo data. |
| `util/PasswordUtil.java` | BCrypt hash and verification helper. |
| `util/SessionManager.java` | Singleton current-user session. |
| `util/SceneManager.java` | Central FXML loading, stage sizing, role routing, and logout. |
| `util/ValidationUtil.java` | Blank, email, Bangladesh phone, and password validation. |
| `util/AlertUtil.java` | User-friendly error, warning, info, and confirmation dialogs. |
| `util/PasswordDialog.java` | Masked, confirmed administrator reset-password dialog. |
| `util/ChipTableCells.java` | Reusable colored status/badge/role table cells. |

## Resources

| File | Purpose |
|---|---|
| `resources/com/bloodlink/config/application.properties` | DB and auto-refresh configuration with environment fallbacks. |
| `resources/com/bloodlink/css/theme.css` | Complete design system and component states. |
| `resources/com/bloodlink/sql/schema.sql` | Runtime schema loaded by `DatabaseSetup`. |
| `resources/com/bloodlink/view/login.fxml` | Branded authentication screen. |
| `resources/com/bloodlink/view/register.fxml` | Adaptive donor/requester registration screen. |
| `resources/com/bloodlink/view/donor_dashboard.fxml` | Donor tabbed workspace. |
| `resources/com/bloodlink/view/requester_dashboard.fxml` | Requester tabbed workspace. |
| `resources/com/bloodlink/view/admin_dashboard.fxml` | Administrator analytics and management workspace. |

## Tests

| File | Purpose |
|---|---|
| `test/model/BloodGroupTest.java` | ABO/Rh compatibility rules. |
| `test/model/BadgeTierTest.java` | Badge boundaries. |
| `test/service/EligibilityServiceTest.java` | Age, weight, and cooldown behavior. |
| `test/util/ValidationUtilTest.java` | Email, Bangladesh phone, and password rules. |

## Traceability references

`docs/reference/` preserves the exact uploaded proposal, README, Maven file, and `.gitignore`. These files are evidence only and are not used by the build.
