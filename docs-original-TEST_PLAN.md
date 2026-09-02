# Test and Verification Plan

## Automated tests

Run:

```bash
mvn test
```

Included tests cover:

- ABO/Rh compatibility
- universal and restricted donor behavior
- badge thresholds
- age/weight/cooldown eligibility
- Bangladesh phone validation
- email validation
- password strength validation

## Manual acceptance tests

### Authentication
1. Run database setup.
2. Sign in with each demo role.
3. Enter a wrong password and confirm a friendly error.
4. Suspend a user as admin and confirm login is blocked.
5. Register a donor and confirm login is blocked until approval.

### Donor workflow
1. Log in as the donor demo account.
2. Change availability and refresh; confirm persistence.
3. Open a `NOTIFIED` match and accept it.
4. Confirm the donor cannot accept the same match again.
5. Check notification unread count and mark messages read.
6. Edit profile and password, log out, and sign in with the new password.

### Requester workflow
1. Submit a request with all required fields.
2. Confirm it appears in request history.
3. Select it and inspect ranked donors and score reasons.
4. Cancel an open request and confirm status/history update.
5. For an accepted request, mark fulfilled.
6. Confirm a donation record appears for the accepted donor.

### Admin workflow
1. Verify charts and summary cards contain seeded data.
2. Search users and requests.
3. Approve a pending donor.
4. Suspend and reactivate a non-admin user.
5. Reset a password using the masked confirmation dialog.
6. Escalate an open request and close another request.
7. Confirm each action appears in the audit log.

### Integrity tests
1. Attempt fulfillment before acceptance; it must fail.
2. Have all notified donors decline; status must become `DECLINED`.
3. Re-run matching; request may return to `MATCHED` when candidates exist.
4. Accept one donor; other notified matches must become `EXPIRED`.
5. Fulfill once; a second fulfillment attempt must fail and no duplicate donation may be created.

## Static verification already performed during generation

- All production Java sources compiled with `javac --release 21` against JavaFX API stubs.
- All test sources compiled against JUnit API stubs.
- XML parsing and FXML/controller binding checks are part of `scripts/verify-project.py`.

A real Maven dependency download, JavaFX launch, and MySQL integration run still require the target machine with Maven and MySQL installed.
