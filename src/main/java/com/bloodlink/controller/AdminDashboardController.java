package com.bloodlink.controller;

import com.bloodlink.dao.AdminDAO;
import com.bloodlink.dao.PagedResult;
import com.bloodlink.model.*;
import com.bloodlink.service.AdminService;
import com.bloodlink.service.RequestService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class AdminDashboardController {
    @FXML private Label welcomeLabel;
    @FXML private Label totalDonorsLabel;
    @FXML private Label pendingRequestsLabel;
    @FXML private Label activeRequestsLabel;
    @FXML private Label fulfillmentRateLabel;
    @FXML private Label statusMessageLabel;
    @FXML private BarChart<String, Number> demandChart;
    @FXML private LineChart<String, Number> monthlyChart;
    @FXML private PieChart statusChart;

    @FXML private TextField userSearchField;
    @FXML private Label userPageLabel;
    @FXML private TableView<AdminUserRow> userTable;
    @FXML private TableColumn<AdminUserRow, Long> userIdColumn;
    @FXML private TableColumn<AdminUserRow, String> userNameColumn;
    @FXML private TableColumn<AdminUserRow, String> userEmailColumn;
    @FXML private TableColumn<AdminUserRow, Role> userRoleColumn;
    @FXML private TableColumn<AdminUserRow, String> userDistrictColumn;
    @FXML private TableColumn<AdminUserRow, String> userApprovedColumn;
    @FXML private TableColumn<AdminUserRow, String> userActiveColumn;
    @FXML private TableColumn<AdminUserRow, LocalDateTime> userCreatedColumn;

    @FXML private TextField requestSearchField;
    @FXML private Label requestPageLabel;
    @FXML private TableView<BloodRequest> requestTable;
    @FXML private TableColumn<BloodRequest, Long> requestIdColumn;
    @FXML private TableColumn<BloodRequest, String> requesterColumn;
    @FXML private TableColumn<BloodRequest, BloodGroup> requestBloodColumn;
    @FXML private TableColumn<BloodRequest, Integer> requestUnitsColumn;
    @FXML private TableColumn<BloodRequest, String> requestProgressColumn;
    @FXML private TableColumn<BloodRequest, Urgency> requestUrgencyColumn;
    @FXML private TableColumn<BloodRequest, String> requestHospitalColumn;
    @FXML private TableColumn<BloodRequest, String> requestDistrictColumn;
    @FXML private TableColumn<BloodRequest, RequestStatus> requestStatusColumn;
    @FXML private TableColumn<BloodRequest, LocalDateTime> requestCreatedColumn;

    @FXML private TableView<DemandRow> demandTable;
    @FXML private TableColumn<DemandRow, BloodGroup> demandBloodColumn;
    @FXML private TableColumn<DemandRow, Long> demandPendingColumn;
    @FXML private TableColumn<DemandRow, Long> demandAvailableColumn;
    @FXML private TableColumn<DemandRow, Long> demandGapColumn;

    @FXML private TableView<DistrictDemandRow> districtDemandTable;
    @FXML private TableColumn<DistrictDemandRow, String> districtDemandDistrictColumn;
    @FXML private TableColumn<DistrictDemandRow, Long> districtDemandPendingColumn;
    @FXML private TableColumn<DistrictDemandRow, Long> districtDemandAvailableColumn;
    @FXML private TableColumn<DistrictDemandRow, Long> districtDemandGapColumn;

    @FXML private TableView<AuditEntry> auditTable;
    @FXML private Label auditPageLabel;
    @FXML private TableColumn<AuditEntry, LocalDateTime> auditTimeColumn;
    @FXML private TableColumn<AuditEntry, String> auditActorColumn;
    @FXML private TableColumn<AuditEntry, String> auditActionColumn;
    @FXML private TableColumn<AuditEntry, String> auditEntityColumn;
    @FXML private TableColumn<AuditEntry, String> auditDetailsColumn;

    private final AdminDAO adminDAO = new AdminDAO();
    private final AdminService adminService = new AdminService();
    private final RequestService requestService = new RequestService();
    private Admin admin;
    private Timeline refreshTimeline;
    private volatile boolean refreshInFlight = false;
    private final AtomicLong userSearchGeneration = new AtomicLong();
    private final AtomicLong requestSearchGeneration = new AtomicLong();
    private int userPage = 1;
    private int requestPage = 1;
    private int auditPage = 1;
    private int userTotalPages = 1;
    private int requestTotalPages = 1;
    private int auditTotalPages = 1;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Admin currentAdmin)) {
            SceneManager.showLogin(); return;
        }
        admin = currentAdmin;
        welcomeLabel.setText("Administrator — " + admin.getFullName());
        PushClient.getInstance().connect(admin.getId());
        PushClient.getInstance().onRefresh(this::refreshAll);
        configureTables();
        userSearchField.textProperty().addListener((obs, oldValue, newValue) -> { userPage = 1; loadUsers(); });
        requestSearchField.textProperty().addListener((obs, oldValue, newValue) -> { requestPage = 1; loadRequests(); });
        refreshAll();
        int seconds = Math.max(8, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void configureTables() {
        userIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        userNameColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().fullName()));
        userEmailColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().email()));
        userRoleColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().role()));
        userDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        userApprovedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().approved() ? "Approved" : "Pending"));
        userActiveColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().active() ? "Active" : "Suspended"));
        userCreatedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));

        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        requesterColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().requesterName()));
        requestBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        requestUnitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsNeeded()));
        requestProgressColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsFulfilled() + " / " + v.getValue().unitsNeeded()));
        requestUrgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        requestHospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        requestDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        requestStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().status()));
        requestCreatedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));

        demandBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        demandPendingColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().pendingRequests()));
        demandAvailableColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().availableDonors()));
        demandGapColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().pendingRequests() - v.getValue().availableDonors()));

        districtDemandDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        districtDemandPendingColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().pendingRequests()));
        districtDemandAvailableColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().availableDonors()));
        districtDemandGapColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().gap()));

        auditTimeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));
        auditActorColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().actorName() == null ? "System" : v.getValue().actorName()));
        auditActionColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().action()));
        auditEntityColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().entityType() + (v.getValue().entityId() == null ? "" : " #" + v.getValue().entityId())));
        auditDetailsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().details()));

        userRoleColumn.setCellFactory(ChipTableCells.forValues());
        userApprovedColumn.setCellFactory(ChipTableCells.forValues());
        userActiveColumn.setCellFactory(ChipTableCells.forValues());
        requestUrgencyColumn.setCellFactory(ChipTableCells.forValues());
        requestStatusColumn.setCellFactory(ChipTableCells.forValues());

        userTable.setPlaceholder(emptyState("No users match this search."));
        requestTable.setPlaceholder(emptyState("No blood requests match this search."));
        demandTable.setPlaceholder(emptyState("No demand data is available yet."));
        districtDemandTable.setPlaceholder(emptyState("No geographic demand data is available yet."));
        auditTable.setPlaceholder(emptyState("No audit events have been recorded yet."));
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        return label;
    }

    /**
     * Runs the full dashboard refresh (stats, all three charts, users, requests,
     * demand, audit log -- up to 8 queries) off the JavaFX Application Thread.
     * This is the most query-heavy screen in the app, so it's the one where the
     * old synchronous-on-the-FX-thread pattern would have hurt most at scale.
     */
    @FXML private void refreshAll() {
        if (refreshInFlight) return;
        refreshInFlight = true;
        String userSearch = userSearchField.getText();
        String requestSearch = requestSearchField.getText();
        BackgroundTasks.run(() -> loadDashboardData(userSearch, requestSearch),
                data -> { applyDashboardData(data); refreshInFlight = false; },
                error -> { statusMessageLabel.setText("Refresh failed: " + error.getMessage()); refreshInFlight = false; });
    }

    private AdminDashboardData loadDashboardData(String userSearch, String requestSearch) throws SQLException {
        return new AdminDashboardData(
                adminDAO.loadStats(),
                adminDAO.requestsByBloodGroup(),
                adminDAO.monthlyRequests(6),
                adminDAO.requestsByStatus(),
                adminDAO.findUsers(userSearch, userPage),
                adminDAO.findRequests(requestSearch, requestPage),
                adminDAO.demandRows(),
                adminDAO.districtDemand(),
                adminDAO.auditEntries(auditPage));
    }

    private void applyDashboardData(AdminDashboardData data) {
        totalDonorsLabel.setText(String.valueOf(data.stats().totalDonors()));
        pendingRequestsLabel.setText(String.valueOf(data.stats().pendingRequests()));
        activeRequestsLabel.setText(String.valueOf(data.stats().activeRequests()));
        fulfillmentRateLabel.setText(String.format("%.1f%%", data.stats().fulfillmentRate()));
        applyCharts(data.demandByGroup(), data.monthlyRequests(), data.requestsByStatus());
        applyUsers(data.users());
        applyRequests(data.requests());
        demandTable.setItems(FXCollections.observableArrayList(data.demandRows()));
        districtDemandTable.setItems(FXCollections.observableArrayList(data.districtDemand()));
        applyAudit(data.auditEntries());
        statusMessageLabel.setText("Last refreshed successfully");
    }

    private void applyUsers(PagedResult<AdminUserRow> result) {
        userTable.setItems(FXCollections.observableArrayList(result.items()));
        userPage = result.page();
        userTotalPages = result.totalPages();
        userPageLabel.setText(pageLabelText(result));
    }

    private void applyRequests(PagedResult<BloodRequest> result) {
        requestTable.setItems(FXCollections.observableArrayList(result.items()));
        requestPage = result.page();
        requestTotalPages = result.totalPages();
        requestPageLabel.setText(pageLabelText(result));
    }

    private void applyAudit(PagedResult<AuditEntry> result) {
        auditTable.setItems(FXCollections.observableArrayList(result.items()));
        auditPage = result.page();
        auditTotalPages = result.totalPages();
        auditPageLabel.setText(pageLabelText(result));
    }

    private String pageLabelText(PagedResult<?> result) {
        return "Page " + result.page() + " of " + result.totalPages() + " (" + result.totalCount() + " total)";
    }

    private void applyCharts(Map<BloodGroup, Long> demandByGroup, Map<YearMonth, Long> monthly, Map<RequestStatus, Long> byStatus) {
        demandChart.getData().clear();
        XYChart.Series<String, Number> demandSeries = new XYChart.Series<>();
        demandSeries.setName("All requests");
        for (Map.Entry<BloodGroup, Long> entry : demandByGroup.entrySet())
            demandSeries.getData().add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
        demandChart.getData().add(demandSeries);

        monthlyChart.getData().clear();
        XYChart.Series<String, Number> monthlySeries = new XYChart.Series<>();
        monthlySeries.setName("Requests");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy");
        for (Map.Entry<YearMonth, Long> entry : monthly.entrySet())
            monthlySeries.getData().add(new XYChart.Data<>(entry.getKey().format(formatter), entry.getValue()));
        monthlyChart.getData().add(monthlySeries);

        statusChart.getData().clear();
        byStatus.forEach((status, count) -> statusChart.getData().add(new PieChart.Data(status.name(), count)));
    }

    private record AdminDashboardData(DashboardStats stats, Map<BloodGroup, Long> demandByGroup,
                                      Map<YearMonth, Long> monthlyRequests, Map<RequestStatus, Long> requestsByStatus,
                                      PagedResult<AdminUserRow> users, PagedResult<BloodRequest> requests,
                                      List<DemandRow> demandRows, List<DistrictDemandRow> districtDemand,
                                      PagedResult<AuditEntry> auditEntries) { }

    /**
     * Search-as-you-type on the user/request tables now runs in the background too.
     * A generation counter is used instead of a simple in-flight flag because these
     * fire on every keystroke: without it, a slower search for an earlier keystroke
     * could complete after a faster one for a later keystroke and overwrite the
     * table with stale results. Whichever search started most recently wins.
     */
    private void loadUsers() {
        long generation = userSearchGeneration.incrementAndGet();
        String search = userSearchField.getText();
        int page = userPage;
        BackgroundTasks.run(() -> adminDAO.findUsers(search, page),
                result -> { if (generation == userSearchGeneration.get()) applyUsers(result); },
                error -> statusMessageLabel.setText(error.getMessage()));
    }

    private void loadRequests() {
        long generation = requestSearchGeneration.incrementAndGet();
        String search = requestSearchField.getText();
        int page = requestPage;
        BackgroundTasks.run(() -> adminDAO.findRequests(search, page),
                result -> { if (generation == requestSearchGeneration.get()) applyRequests(result); },
                error -> statusMessageLabel.setText(error.getMessage()));
    }

    private void loadAudit() {
        int page = auditPage;
        BackgroundTasks.run(() -> adminDAO.auditEntries(page), this::applyAudit,
                error -> statusMessageLabel.setText(error.getMessage()));
    }

    @FXML private void previousUserPage() {
        if (userPage <= 1) return;
        userPage--; loadUsers();
    }

    @FXML private void nextUserPage() {
        if (userPage >= userTotalPages) return;
        userPage++; loadUsers();
    }

    @FXML private void previousRequestPage() {
        if (requestPage <= 1) return;
        requestPage--; loadRequests();
    }

    @FXML private void nextRequestPage() {
        if (requestPage >= requestTotalPages) return;
        requestPage++; loadRequests();
    }

    @FXML private void previousAuditPage() {
        if (auditPage <= 1) return;
        auditPage--; loadAudit();
    }

    @FXML private void nextAuditPage() {
        if (auditPage >= auditTotalPages) return;
        auditPage++; loadAudit();
    }

    @FXML private void approveSelectedUser() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        showResult(adminService.setApproved(selected.id(), true, admin.getId())); refreshAll();
    }

    @FXML private void suspendSelectedUser() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        if (!AlertUtil.confirm("Suspend user", "Suspend " + selected.fullName() + "?")) return;
        showResult(adminService.setActive(selected.id(), false, admin.getId())); refreshAll();
    }

    @FXML private void activateSelectedUser() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        showResult(adminService.setActive(selected.id(), true, admin.getId())); refreshAll();
    }

    @FXML private void resetSelectedPassword() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        PasswordDialog.show("Reset password", "Set a temporary password for " + selected.fullName())
                .ifPresent(password -> showResult(adminService.resetPassword(selected.id(), password, admin.getId())));
    }

    @FXML private void escalateSelectedRequest() {
        BloodRequest selected = selectedRequest(); if (selected == null) return;
        showResult(requestService.adminTransition(selected.id(), admin.getId(), RequestStatus.ESCALATED, "Manually escalated by admin"));
        refreshAll();
    }

    @FXML private void closeSelectedRequest() {
        BloodRequest selected = selectedRequest(); if (selected == null) return;
        if (!AlertUtil.confirm("Close request", "Close request #" + selected.id() + " as cancelled?")) return;
        showResult(requestService.adminTransition(selected.id(), admin.getId(), RequestStatus.CANCELLED, "Closed by admin"));
        refreshAll();
    }

    private AdminUserRow selectedUser() {
        AdminUserRow selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) AlertUtil.warning("No user selected", "Select a user first.");
        else if (selected.role() == Role.ADMIN) { AlertUtil.warning("Protected account", "Administrator accounts cannot be changed here."); return null; }
        return selected;
    }

    private BloodRequest selectedRequest() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) AlertUtil.warning("No request selected", "Select a request first.");
        return selected;
    }

    private void showResult(ServiceResult<Void> result) {
        if (result.success()) AlertUtil.info("Success", result.message()); else AlertUtil.error("Action failed", result.message());
    }

    @FXML private void logout() {
        if (refreshTimeline != null) refreshTimeline.stop();
        PushClient.getInstance().disconnect();
        SceneManager.logout();
    }
}
