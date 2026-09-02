# Project Analysis and Source Reconciliation

## Reviewed source materials

1. Proposal slide deck: 14 slides covering the problem, differentiation, objectives, core and advanced features, roles, modules, UI concepts, stack, and timeline.
2. Uploaded Maven file: Java 17, JavaFX 17, MySQL connector, jBCrypt, and JUnit.
3. Uploaded README: describes a layered working skeleton and several files that were not included in the upload.
4. Uploaded `.gitignore`: excludes Maven/IDE outputs, logs, and `.env`.

## Conflicts and resolutions

| Conflict or gap | Resolution |
|---|---|
| Proposal and uploaded build specify Java 17, while the user explicitly requires Java 21 | Java 21 is the controlling requirement. All source and build configuration use release 21 and JavaFX 21. |
| README says the repository already contains controllers, models, DAOs, FXML, CSS, and `database/schema.sql`, but those files were not uploaded | The missing application was implemented from scratch while preserving the README's intended layered architecture. |
| “Real-time” is requested, but the specified stack is a standalone JavaFX/JDBC desktop application with no Spring server or push service | In-app data and notifications refresh on a configurable polling interval, default 10 seconds. No unsupported external API was invented. |
| Location-based matching is mentioned, but no GPS/maps API is specified | Matching uses exact district priority, as supported by the proposal. |
| Password reset is requested without email/SMS APIs | Authenticated users change their own passwords; administrators can securely assign a temporary password through a masked confirmation dialog. |
| Proposal asks for an audit trail but does not define the table | `request_status_history` records lifecycle transitions, and `audit_logs` records important user/admin operations. |

## Application purpose

BloodLink reduces emergency donor-search delay by replacing unstructured social-media coordination with an auditable workflow. Requesters submit an emergency need; the system filters and ranks compatible eligible donors; donors respond; requesters confirm fulfillment; administrators supervise users, demand, and system activity.

## Complete feature checklist

### Authentication and authorization
- [x] Donor and requester registration
- [x] Role-aware login for Donor, Requester, and Admin
- [x] BCrypt password hashing and verification
- [x] Session singleton
- [x] Donor approval gate
- [x] Suspended account protection
- [x] Role-specific screens
- [x] Profile editing
- [x] Authenticated password change
- [x] Administrator temporary-password reset

### Donor management
- [x] Blood group, birth date, weight, last donation date
- [x] Availability states
- [x] Eligibility reasons
- [x] 56-day cooldown progress
- [x] Donation history
- [x] Verified donation count
- [x] Bronze, Silver, Gold, Platinum badges
- [x] Accept/decline response handling

### Requests and matching
- [x] Blood group, units, urgency, hospital, district, deadline, notes
- [x] Prepared-statement persistence
- [x] Blood compatibility filter
- [x] District, exact-group, cooldown, experience, and urgency ranking
- [x] Maximum eight ranked candidates
- [x] Match reason text
- [x] Duplicate-match prevention
- [x] Re-matching after all donors decline
- [x] Donor and requester notifications

### Lifecycle
- [x] PENDING
- [x] MATCHED
- [x] ACCEPTED
- [x] DECLINED
- [x] FULFILLED
- [x] CANCELLED
- [x] ESCALATED
- [x] Transition validation
- [x] Accepted donor locking
- [x] Competing match expiration
- [x] Donation record creation on fulfillment
- [x] Donor cooldown/count update on fulfillment
- [x] Request lifecycle history

### Notifications
- [x] Match notification
- [x] Response notification
- [x] Fulfillment notification
- [x] Cancellation notification
- [x] Unread counter
- [x] Mark one or all read
- [x] Notification history panel
- [x] Configurable automatic refresh

### Administrator
- [x] Total-donor, pending, active, and fulfillment cards
- [x] Requests-by-blood-group BarChart
- [x] Monthly-request LineChart
- [x] Request-status PieChart
- [x] Demand versus available donor table
- [x] Sortable/searchable user table
- [x] Searchable request table
- [x] Approve/suspend/activate/reset
- [x] Escalate/close request
- [x] Audit log viewer

### UI/UX
- [x] Shared teal/rose design system
- [x] Consistent top bars, cards, tabs, tables, forms, and dialogs
- [x] Hover, pressed, focus, disabled states
- [x] Status chips
- [x] Inline field and action messages
- [x] Empty states
- [x] Scrollable/adaptable dashboards
- [x] Confirmation dialogs for destructive actions
- [x] User-friendly exception messages

## Out-of-scope items not supported by the uploaded requirements

- Hospital-side user role
- SMS/email/WhatsApp notifications
- Live GPS distance or maps
- Blood bank inventory
- Online deployment or multi-device synchronization
- Spring Boot or web frontend

These were not silently added because the uploaded proposal explicitly selects a JavaFX/MySQL desktop architecture and says no email API is required.
