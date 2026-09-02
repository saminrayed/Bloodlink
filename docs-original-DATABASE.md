# Database Design

## Relationship overview

```text
users 1 ─── 0..1 donor_profiles
users 1 ─── * blood_requests (requester_id)
users 1 ─── * request_matches (donor_id)
blood_requests 1 ─── * request_matches
blood_requests 1 ─── * request_status_history
users 1 ─── * notifications
blood_requests 0..1 ─── * notifications
users 1 ─── * donation_history (donor_id)
blood_requests 0..1 ─── 0..1 donation_history
users 0..1 ─── * audit_logs
```

## Tables

### `users`
Shared identity, authentication, role, approval, active state, and contact information. Email is unique.

### `donor_profiles`
One-to-one donor extension containing blood group, health information, availability, and verified donation count.

### `blood_requests`
Emergency request data and current lifecycle state. `accepted_donor_id` is nullable until a donor accepts.

### `request_matches`
Ranked request-to-donor candidates. The `(request_id, donor_id)` pair is unique.

### `request_status_history`
Immutable transition history containing from/to state, actor, note, and time.

### `notifications`
Per-user in-app messages with unread state and optional request relation.

### `donation_history`
Verified donations. `request_id` is unique so one request cannot produce duplicate donation credit.

### `audit_logs`
Administrative and security-sensitive application actions.

## Initialization

The canonical source is:

```text
src/main/resources/com/bloodlink/sql/schema.sql
```

A convenient duplicate is included at:

```text
database/schema.sql
```

Run:

```bash
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.bloodlink.util.DatabaseSetup
```

The setup utility:

1. Parses the configured MySQL JDBC URL.
2. Creates the database if needed.
3. Executes every schema statement.
4. Upserts demo accounts and donor profiles.
5. Adds representative request, match, history, notification, donation, and audit records.

It is safe to run repeatedly; demo users are upserted and demo request seeding is guarded.
