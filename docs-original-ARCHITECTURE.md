# Architecture

## Layered design

```text
JavaFX FXML + CSS
        │
        ▼
Controllers
        │
        ▼
Services ───── Domain models/enums
        │
        ▼
DAOs + JDBC transactions
        │
        ▼
MySQL 8+
```

### View layer

Five complete screens are defined in FXML:

- `login.fxml`
- `register.fxml`
- `donor_dashboard.fxml`
- `requester_dashboard.fxml`
- `admin_dashboard.fxml`

All screens use one stylesheet, so visual changes remain consistent.

### Controller layer

Controllers bind FXML controls, handle navigation and selection state, call services, and display safe messages. Controllers contain no SQL.

### Service layer

- `AuthService`: login and registration rules
- `EligibilityService`: age, weight, and cooldown rules
- `MatchingService`: compatibility and candidate ranking
- `RequestService`: request lifecycle façade
- `DonorService`: availability and health-profile validation
- `ProfileService`: personal information and password changes
- `NotificationService`: inbox operations
- `AdminService`: protected administrator actions

### DAO layer

DAOs own prepared SQL, result mapping, transaction boundaries, status locking, and audit writes. Multi-table lifecycle operations use transactions with rollback.

### Domain layer

Models represent users, requests, matches, notifications, donations, charts, demand rows, and audit/history rows. Enums prevent invalid role/status/blood-group strings inside Java code.

## Main workflows

### Registration and login

1. User selects Donor or Requester.
2. Registration fields are validated.
3. Password is BCrypt-hashed.
4. Requesters are approved immediately; donors wait for administrator approval.
5. Login loads the complete role-specific model into `SessionManager`.
6. `SceneManager` opens the corresponding dashboard.

### Emergency request

1. Requester submits validated request data.
2. DAO creates `PENDING` request and initial history row.
3. Matching service loads approved/active/available donors.
4. Eligibility and blood compatibility filters are applied.
5. Candidates are ranked and the top eight are persisted.
6. Request moves to `MATCHED` when candidates exist.
7. Donors receive in-app notifications.

### Donor response

1. Donor accepts or declines only a current `NOTIFIED` match.
2. The request row is locked during the transaction.
3. Acceptance sets one accepted donor and expires competing matches.
4. Declining the last open match changes the request to `DECLINED`.
5. Requester receives a response notification.

### Fulfillment

1. Only the request owner may fulfill an `ACCEPTED` request.
2. Request becomes `FULFILLED`.
3. A verified donation record is inserted.
4. Donor donation count and last-donation date are updated.
5. Donor availability changes to `BUSY` for safe manual review.
6. Cooldown and badge display change automatically.

### Administration

1. Administrator searches users or requests.
2. Account and request actions pass through protected service methods.
3. Analytics are calculated from live SQL aggregates.
4. Every important action appears in the audit table.

## Security and integrity

- BCrypt hashes; no plaintext passwords in the database
- Environment-based credentials
- Prepared statements
- Role and ownership checks
- Account approval/active checks
- Transactional lifecycle transitions
- Foreign keys, unique constraints, checks, and indexes
- Masked password reset dialog
- Protected administrator accounts in the user-management screen
