package com.bloodlink.controller;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.HospitalDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.*;
import com.bloodlink.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;

public final class DonorDashboardController {
    @FXML private Label welcomeLabel;
    @FXML private Label bloodGroupLabel;
    @FXML private Label badgeLabel;
    @FXML private Label eligibilityLabel;
    @FXML private Label cooldownLabel;
    @FXML private ProgressBar cooldownProgress;
    @FXML private Label unreadLabel;
    @FXML private ComboBox<AvailabilityStatus> availabilityCombo;

    @FXML private Label impactDonationsLabel;
    @FXML private Label impactUnitsLabel;
    @FXML private Label impactHospitalsLabel;
    @FXML private Label impactSinceLabel;
    @FXML private Label impactRatingLabel;

    @FXML private TableView<HospitalWithDistance> nearbyHospitalTable;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalNameColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalDistrictColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalAreaColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalPhoneColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalDistanceColumn;

    @FXML private TableView<DonorMatchView> matchTable;
    @FXML private TableColumn<DonorMatchView, Long> requestIdColumn;
    @FXML private TableColumn<DonorMatchView, BloodGroup> matchBloodColumn;
    @FXML private TableColumn<DonorMatchView, String> hospitalColumn;
    @FXML private TableColumn<DonorMatchView, String> matchDistrictColumn;
    @FXML private TableColumn<DonorMatchView, Urgency> urgencyColumn;
    @FXML private TableColumn<DonorMatchView, LocalDate> deadlineColumn;
    @FXML private TableColumn<DonorMatchView, RequestStatus> requestStatusColumn;
    @FXML private TableColumn<DonorMatchView, MatchStatus> matchStatusColumn;
    @FXML private TableColumn<DonorMatchView, Double> scoreColumn;
    @FXML private TableColumn<DonorMatchView, String> distanceColumn;
    @FXML private TableColumn<DonorMatchView, String> requesterRatingColumn;
    @FXML private TableColumn<DonorMatchView, String> progressColumn;
    @FXML private TableColumn<DonorMatchView, String> handshakeColumn;

    @FXML private TableView<DonationRecord> donationTable;
    @FXML private TableColumn<DonationRecord, LocalDate> donationDateColumn;
    @FXML private TableColumn<DonationRecord, String> donationHospitalColumn;
    @FXML private TableColumn<DonationRecord, BloodGroup> donationBloodColumn;
    @FXML private TableColumn<DonationRecord, Integer> donationUnitsColumn;
    @FXML private TableColumn<DonationRecord, String> donationVerifiedColumn;
    @FXML private TableColumn<DonationRecord, String> donationReviewColumn;

    @FXML private ListView<Notification> notificationList;

    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Button uploadPhotoButton;
    @FXML private Button removePhotoButton;
    @FXML private TextField weightField;
    @FXML private DatePicker lastDonationPicker;
    @FXML private ComboBox<Hospital> referenceHospitalCombo;
    @FXML private Label referenceHospitalHelperLabel;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;

    private final DonorDAO donorDAO = new DonorDAO();
    private final HospitalDAO hospitalDAO = new HospitalDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final NotificationService notificationService = new NotificationService();
    private final DonorService donorService = new DonorService();
    private final RequestService requestService = new RequestService();
    private final ProfileService profileService = new ProfileService();
    private final EligibilityService eligibilityService = new EligibilityService();
    private final ReviewService reviewService = new ReviewService();
    private final LocationService locationService = new LocationService();
    private Donor donor;
    private Timeline refreshTimeline;
    private volatile boolean refreshInFlight = false;
    private java.util.Set<Long> reviewedRequestIds = java.util.Set.of();
    private boolean suppressReferenceHospitalSearch = false;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Donor currentDonor)) {
            SceneManager.showLogin(); return;
        }
        donor = currentDonor;
        configureTables();
        configureReferenceHospitalPicker();
        PushClient.getInstance().connect(donor.getId());
        PushClient.getInstance().onRefresh(this::refreshAll);
        availabilityCombo.getItems().setAll(AvailabilityStatus.values());
        availabilityCombo.setValue(donor.getAvailabilityStatus());
        availabilityCombo.setOnAction(event -> updateAvailability());
        notificationList.setOnMouseClicked(event -> markSelectedNotificationRead());
        populateProfile();
        refreshAll();
        int seconds = Math.max(5, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void configureTables() {
        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().requestId()));
        matchBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        hospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        matchDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        urgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        deadlineColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().deadline()));
        requestStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().requestStatus()));
        matchStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().matchStatus()));
        scoreColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().score()));
        distanceColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(formatDistance(v.getValue().distanceKm())));
        requesterRatingColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(formatRating(v.getValue().requesterRating(), v.getValue().requesterReviewCount())));
        progressColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsFulfilled() + " / " + v.getValue().unitsNeeded() + " units"));
        handshakeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(formatHandshake(v.getValue())));
        donationDateColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().donationDate()));
        donationHospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        donationBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        donationUnitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().units()));
        donationVerifiedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().verified() ? "Verified" : "Pending"));
        donationReviewColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().requestId() != null && reviewedRequestIds.contains(v.getValue().requestId()) ? "Rated" : "Not rated yet"));

        urgencyColumn.setCellFactory(ChipTableCells.forValues());
        requestStatusColumn.setCellFactory(ChipTableCells.forValues());
        matchStatusColumn.setCellFactory(ChipTableCells.forValues());
        donationVerifiedColumn.setCellFactory(ChipTableCells.forValues());

        matchTable.setPlaceholder(emptyState("No matching emergency requests are waiting for you."));
        donationTable.setPlaceholder(emptyState("No verified donation history is available yet."));
        notificationList.setPlaceholder(emptyState("You have no notifications."));
        nearbyHospitalNameColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().name()));
        nearbyHospitalDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().district()));
        nearbyHospitalAreaColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().area()));
        nearbyHospitalPhoneColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().hospital().phone() == null || v.getValue().hospital().phone().isBlank() ? "—" : v.getValue().hospital().phone()));
        nearbyHospitalDistanceColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(formatDistance(v.getValue().distanceKm())));
        nearbyHospitalTable.setPlaceholder(emptyState("No hospitals are in the directory yet."));
    }

    private String formatDistance(Double distanceKm) {
        return distanceKm == null ? "—" : String.format("~%.1f km", distanceKm);
    }

    private String formatRating(Double averageRating, long reviewCount) {
        return averageRating == null ? "No reviews yet" : String.format("\u2605 %.1f (%d)", averageRating, reviewCount);
    }

    /** What's actually happening on this donor's own handshake for this match, regardless of the request's overall status. */
    private String formatHandshake(DonorMatchView match) {
        if (match.matchStatus() != MatchStatus.ACCEPTED) return "—";
        if (match.donorConfirmed() && match.requesterConfirmed()) return "Complete";
        if (match.donorConfirmed()) return "Waiting on requester";
        if (match.requesterConfirmed()) return "Waiting on you";
        return "Awaiting both confirmations";
    }

    /**
     * Same searchable-picker pattern as the requester's hospital field, repurposed so
     * a donor can choose their own precise location stand-in -- see LocationService.
     */
    private void configureReferenceHospitalPicker() {
        referenceHospitalCombo.setEditable(true);
        referenceHospitalCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Hospital hospital) { return hospital == null ? "" : hospital.name(); }
            @Override public Hospital fromString(String text) { return null; }
        });
        searchReferenceHospitals("");
        referenceHospitalCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (suppressReferenceHospitalSearch) return;
            searchReferenceHospitals(newText);
        });
    }

    private void searchReferenceHospitals(String query) {
        try {
            referenceHospitalCombo.getItems().setAll(hospitalDAO.search(query, 15));
        } catch (SQLException e) {
            // Search-as-you-type failure should not block the donor from using the field.
        }
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        return label;
    }

    private void populateProfile() {
        welcomeLabel.setText("Welcome, " + donor.getFullName());
        bloodGroupLabel.setText(donor.getBloodGroup().toString());
        badgeLabel.setText(donor.getBadgeTier() + " donor");
        nameField.setText(donor.getFullName());
        phoneField.setText(donor.getPhone());
        districtField.setText(donor.getDistrict());
        addressArea.setText(donor.getAddress());
        weightField.setText(String.valueOf(donor.getWeightKg()));
        lastDonationPicker.setValue(donor.getLastDonationDate());
        populateReferenceHospital();
        applyProfilePhoto();
        updateEligibilityCard();
    }

    /**
     * Loaded via the dedicated ProfileService.loadPhoto(), never as part of the
     * routine donor/session fetch -- see UserDAO.findPhoto's Javadoc for why. Falls
     * back to an initials badge (no image asset, no image-generation tool available
     * in this environment -- this is a real, working substitute, not a placeholder)
     * when no photo is set or the stored bytes can't be decoded as an image.
     */
    private void applyProfilePhoto() {
        java.util.Optional<byte[]> photo = profileService.loadPhoto(donor.getId());
        if (photo.isPresent()) {
            try {
                profilePhotoView.setImage(new Image(new ByteArrayInputStream(photo.get())));
                profilePhotoView.setClip(new Circle(42, 42, 42));
                profilePhotoView.setVisible(true);
                profilePhotoView.setManaged(true);
                profileInitialsLabel.setVisible(false);
                profileInitialsLabel.setManaged(false);
                return;
            } catch (RuntimeException e) {
                // Stored bytes weren't a decodable image -- fall through to the initials badge below.
            }
        }
        profilePhotoView.setVisible(false);
        profilePhotoView.setManaged(false);
        profileInitialsLabel.setVisible(true);
        profileInitialsLabel.setManaged(true);
        profileInitialsLabel.setText(initialsOf(donor.getFullName()));
    }

    private String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @FXML private void uploadPhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a profile photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(uploadPhotoButton.getScene().getWindow());
        if (file == null) return;
        BackgroundTasks.run(
                () -> profileService.updatePhoto(donor.getId(), Files.readAllBytes(file.toPath())),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Could not read that file: " + error.getMessage()));
    }

    @FXML private void removePhoto() {
        BackgroundTasks.run(
                () -> profileService.updatePhoto(donor.getId(), null),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Photo could not be removed: " + error.getMessage()));
    }

    private void populateReferenceHospital() {
        suppressReferenceHospitalSearch = true;
        if (donor.getReferenceHospitalId() == null) {
            referenceHospitalCombo.setValue(null);
            referenceHospitalCombo.getEditor().clear();
            referenceHospitalHelperLabel.setText("Not set -- distance in your matches uses your district instead.");
        } else {
            try {
                hospitalDAO.findById(donor.getReferenceHospitalId()).ifPresentOrElse(
                        hospital -> {
                            referenceHospitalCombo.setValue(hospital);
                            referenceHospitalCombo.getEditor().setText(hospital.name());
                            referenceHospitalHelperLabel.setText("Distance in your matches is measured from here.");
                        },
                        () -> referenceHospitalHelperLabel.setText("Your saved reference hospital is no longer active; distance falls back to your district."));
            } catch (SQLException e) {
                referenceHospitalHelperLabel.setText("Could not load your reference hospital: " + e.getMessage());
            }
        }
        suppressReferenceHospitalSearch = false;
    }

    private void updateEligibilityCard() {
        EligibilityService.EligibilityResult result = eligibilityService.evaluate(donor);
        eligibilityLabel.setText(result.eligible() ? "READY" : "NOT ELIGIBLE");
        cooldownLabel.setText(result.reason());
        cooldownProgress.setProgress(result.cooldownDaysRemaining() == 0 ? 1.0 : 1.0 - result.cooldownDaysRemaining() / 56.0);
        eligibilityLabel.getStyleClass().removeAll("status-success", "status-warning");
        eligibilityLabel.getStyleClass().add(result.eligible() ? "status-success" : "status-warning");
    }

    /**
     * Runs the dashboard's periodic refresh off the JavaFX Application Thread.
     * {@code refreshInFlight} skips a tick rather than queuing another background
     * fetch if the previous one hasn't finished yet (e.g. a slow connection).
     */
    @FXML private void refreshAll() {
        if (refreshInFlight) return;
        refreshInFlight = true;
        BackgroundTasks.run(this::loadDashboardData,
                data -> { applyDashboardData(data); refreshInFlight = false; },
                error -> { profileMessageLabel.setText("Refresh failed: " + error.getMessage()); refreshInFlight = false; });
    }

    private DonorDashboardData loadDashboardData() throws SQLException {
        java.util.List<DonationRecord> donations = donorDAO.findDonationHistory(donor.getId());
        return new DonorDashboardData(
                requestDAO.findMatchesForDonor(donor.getId(), donor.getDistrict(), donor.getReferenceHospitalId()),
                donations,
                notificationService.list(donor.getId()),
                notificationService.unreadCount(donor.getId()),
                reviewService.reviewedRequestIdsBy(donor.getId()),
                reviewService.reputationOf(donor.getId()),
                loadNearbyHospitals());
    }

    /**
     * The general "browse hospitals" view the matched-requests list can't cover, since
     * that only ever shows hospitals tied to an actual active request. Sorted by
     * distance from the donor (nulls -- unknown distance -- sorted last, never treated
     * as "far" the way the matching radius filter treats them, since this is just a
     * browsing list, not an inclusion decision).
     */
    private java.util.List<HospitalWithDistance> loadNearbyHospitals() throws SQLException {
        java.util.List<HospitalWithDistance> rows = new java.util.ArrayList<>();
        for (Hospital hospital : hospitalDAO.findAll()) {
            Double distanceKm = locationService.distanceKm(donor.getDistrict(), donor.getReferenceHospitalId(),
                    hospital.latitude(), hospital.longitude()).orElse(null);
            rows.add(new HospitalWithDistance(hospital, distanceKm));
        }
        rows.sort(java.util.Comparator.comparing(HospitalWithDistance::distanceKm,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return rows;
    }

    private record HospitalWithDistance(Hospital hospital, Double distanceKm) { }

    private void applyDashboardData(DonorDashboardData data) {
        matchTable.setItems(FXCollections.observableArrayList(data.matches()));
        donationTable.setItems(FXCollections.observableArrayList(data.donations()));
        notificationList.setItems(FXCollections.observableArrayList(data.notifications()));
        unreadLabel.setText(String.valueOf(data.unreadCount()));
        reviewedRequestIds = data.reviewedRequestIds();
        donationTable.refresh();
        applyImpactSummary(data.donations(), data.reputation());
        nearbyHospitalTable.setItems(FXCollections.observableArrayList(data.nearbyHospitals()));
    }

    /**
     * Computed from the donation history already fetched every refresh, rather than a
     * separate aggregate query -- this data is already in memory, so there's no reason
     * to hit the database again just to summarize it. Every figure here is a real,
     * verifiable count from donation_history; deliberately no "lives saved" style
     * multiplier, since that's an estimate this app has no basis to assert as fact
     * about a specific donor's specific donations.
     */
    private void applyImpactSummary(java.util.List<DonationRecord> donations, ReputationSummary reputation) {
        if (donations.isEmpty()) {
            impactDonationsLabel.setText("0");
            impactUnitsLabel.setText("0");
            impactHospitalsLabel.setText("0");
            impactSinceLabel.setText("No verified donations yet");
        } else {
            int totalUnits = donations.stream().mapToInt(DonationRecord::units).sum();
            long distinctHospitals = donations.stream().map(DonationRecord::hospitalName).distinct().count();
            LocalDate first = donations.stream().map(DonationRecord::donationDate)
                    .min(LocalDate::compareTo).orElse(null);
            impactDonationsLabel.setText(String.valueOf(donations.size()));
            impactUnitsLabel.setText(String.valueOf(totalUnits));
            impactHospitalsLabel.setText(String.valueOf(distinctHospitals));
            impactSinceLabel.setText(first == null ? "—" : "Donating since " + first);
        }
        impactRatingLabel.setText(formatRating(reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount()));
    }

    private record DonorDashboardData(java.util.List<DonorMatchView> matches, java.util.List<DonationRecord> donations,
                                      java.util.List<Notification> notifications, long unreadCount,
                                      java.util.Set<Long> reviewedRequestIds, ReputationSummary reputation,
                                      java.util.List<HospitalWithDistance> nearbyHospitals) { }

    @FXML private void acceptSelected() {
        DonorMatchView selected = matchTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.NOTIFIED) { AlertUtil.warning("Already answered", "This match is no longer awaiting a response."); return; }
        if (!AlertUtil.confirm("Accept request", "Accept blood request #" + selected.requestId() + "?")) return;
        showResult(requestService.accept(selected.requestId(), donor.getId()));
        refreshAll();
    }

    @FXML private void declineSelected() {
        DonorMatchView selected = matchTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Decline match", "Decline request #" + selected.requestId() + "?")) return;
        showResult(requestService.decline(selected.requestId(), donor.getId()));
        refreshAll();
    }

    /**
     * The donor's half of the two-sided handshake. A donor can only confirm a request
     * they personally accepted and that is still sitting in ACCEPTED or
     * PARTIALLY_FULFILLED (another donor on the same multi-unit request may have
     * already completed their own handshake, moving the overall status along, while
     * this donor's own match is still waiting) -- both checked here and, more
     * importantly, again in RequestDAO.confirmDonorSide, since this button being
     * visible is not itself authorization.
     */
    @FXML private void confirmDonated() {
        DonorMatchView selected = matchTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.ACCEPTED
                || !(selected.requestStatus() == RequestStatus.ACCEPTED || selected.requestStatus() == RequestStatus.PARTIALLY_FULFILLED)) {
            AlertUtil.warning("Not ready to confirm", "You can only confirm a donation for a request you've accepted that is still awaiting confirmation.");
            return;
        }
        if (selected.donorConfirmed()) {
            AlertUtil.info("Already confirmed", "You already confirmed this donation. Waiting on the requester's side.");
            return;
        }
        if (!AlertUtil.confirm("Confirm donation", "Confirm that you donated blood for request #" + selected.requestId() + "?")) return;
        showResult(requestService.confirmDonated(selected.requestId(), donor.getId()));
        refreshAll();
    }

    /**
     * Rating happens from the Donation History tab, since that's where FULFILLED
     * requests are visible to a donor -- findMatchesForDonor deliberately excludes
     * FULFILLED requests once the handshake completes.
     */
    @FXML private void rateRequester() {
        DonationRecord selected = donationTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No donation selected", "Select a donation record first."); return; }
        if (selected.requestId() == null) {
            AlertUtil.warning("Not reviewable", "This donation record isn't linked to a BloodLink request.");
            return;
        }
        if (reviewedRequestIds.contains(selected.requestId())) {
            AlertUtil.info("Already reviewed", "You already rated the requester for this donation.");
            return;
        }
        ReviewDialog.show("Rate requester", "the requester").ifPresent(input -> {
            ServiceResult<Void> result = reviewService.submit(selected.requestId(), donor.getId(), input.rating(), input.tags(), input.comment());
            showResult(result);
            if (result.success()) refreshAll();
        });
    }

    private void updateAvailability() {
        AvailabilityStatus selected = availabilityCombo.getValue();
        if (selected == donor.getAvailabilityStatus()) return;
        ServiceResult<Void> result = donorService.updateAvailability(donor.getId(), selected);
        if (result.success()) donor.setAvailabilityStatus(selected);
        else AlertUtil.error("Update failed", result.message());
    }

    @FXML private void saveProfile() {
        ServiceResult<User> result = profileService.updateProfile(donor.getId(), nameField.getText(), phoneField.getText(),
                districtField.getText(), addressArea.getText());
        if (!result.success()) { profileMessageLabel.setText(result.message()); return; }
        donor.setFullName(result.data().getFullName()); donor.setPhone(result.data().getPhone());
        donor.setDistrict(result.data().getDistrict()); donor.setAddress(result.data().getAddress());
        profileMessageLabel.setText(result.message()); populateProfile();
    }

    @FXML private void saveHealth() {
        ServiceResult<Void> result = donorService.updateHealth(donor.getId(), weightField.getText(), lastDonationPicker.getValue());
        if (result.success()) {
            donor.setWeightKg(Double.parseDouble(weightField.getText().trim()));
            donor.setLastDonationDate(lastDonationPicker.getValue());
            updateEligibilityCard();
        }
        profileMessageLabel.setText(result.message());
    }

    /**
     * Saves whichever hospital the donor picked from the searchable list as their
     * reference point. Free-typed text that doesn't match a real selection is
     * rejected rather than silently ignored, since an unresolved reference would
     * leave the donor thinking their distance is precise when it fell back silently.
     */
    @FXML private void saveReferenceHospital() {
        Hospital selected = referenceHospitalCombo.getValue();
        String typedText = referenceHospitalCombo.getEditor().getText();
        if (typedText == null || typedText.isBlank()) {
            applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), null));
            return;
        }
        if (selected == null || !selected.name().equals(typedText)) {
            profileMessageLabel.setText("Pick a hospital from the dropdown list, or clear the field to remove your reference hospital.");
            return;
        }
        applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), selected.id()));
    }

    @FXML private void clearReferenceHospital() {
        referenceHospitalCombo.setValue(null);
        referenceHospitalCombo.getEditor().clear();
        applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), null));
    }

    private void applyReferenceHospitalResult(ServiceResult<Void> result) {
        if (result.success()) donor.setReferenceHospitalId(referenceHospitalCombo.getValue() == null ? null : referenceHospitalCombo.getValue().id());
        profileMessageLabel.setText(result.message());
        populateReferenceHospital();
    }

    @FXML private void changePassword() {
        ServiceResult<Void> result = profileService.changePassword(donor.getId(), oldPasswordField.getText(),
                newPasswordField.getText(), confirmPasswordField.getText());
        profileMessageLabel.setText(result.message());
        if (result.success()) { oldPasswordField.clear(); newPasswordField.clear(); confirmPasswordField.clear(); }
    }

    @FXML private void markAllNotificationsRead() {
        try { notificationService.markAllRead(donor.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void markSelectedNotificationRead() {
        Notification selected = notificationList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.read()) return;
        try { notificationService.markRead(selected.id(), donor.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
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
