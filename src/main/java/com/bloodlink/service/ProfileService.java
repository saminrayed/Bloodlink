package com.bloodlink.service;

import com.bloodlink.dao.UserDAO;
import com.bloodlink.model.User;
import com.bloodlink.util.PasswordUtil;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ProfileService {
    private final UserDAO userDAO = new UserDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    public ServiceResult<User> updateProfile(long userId, String fullName, String phone, String district, String address) {
        if (ValidationUtil.isBlank(fullName) || fullName.trim().length() < 3) return ServiceResult.failure("Enter your full name.");
        if (!ValidationUtil.isValidPhone(phone)) return ServiceResult.failure("Enter a valid Bangladeshi mobile number.");
        if (ValidationUtil.isBlank(district)) return ServiceResult.failure("District is required.");
        try {
            authorizationService.requireSelfOrAdmin(userId);
            userDAO.updateProfile(userId, fullName, phone, district, address);
            User updated = userDAO.findById(userId).orElseThrow(() -> new SQLException("User not found after update."));
            return ServiceResult.success("Profile updated.", updated);
        } catch (SQLException e) {
            return ServiceResult.failure("Profile could not be updated: " + e.getMessage());
        }
    }

    public ServiceResult<Void> changePassword(long userId, String oldPassword, String newPassword, String confirmation) {
        if (ValidationUtil.isBlank(oldPassword)) return ServiceResult.failure("Enter your current password.");
        List<String> errors = ValidationUtil.validatePassword(newPassword);
        if (!errors.isEmpty()) return ServiceResult.failure(String.join("\n", errors));
        if (!newPassword.equals(confirmation)) return ServiceResult.failure("New password confirmation does not match.");
        try {
            // The current-password check below is real, credential-based protection on its
            // own; requireSelfOrAdmin adds the same session-level defense-in-depth every
            // other self-service method in this session got, rather than being the only
            // gap left relying purely on the old-password check.
            authorizationService.requireSelfOrAdmin(userId);
            if (!PasswordUtil.verify(oldPassword, userDAO.findPasswordHash(userId)))
                return ServiceResult.failure("Current password is incorrect.");
            userDAO.updatePassword(userId, PasswordUtil.hash(newPassword));
            return ServiceResult.success("Password changed securely.", null);
        } catch (SQLException e) {
            return ServiceResult.failure("Password could not be changed: " + e.getMessage());
        }
    }

    /** Kept well below MEDIUMBLOB's real 16MB ceiling -- a profile photo has no business being that large. */
    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    public ServiceResult<Void> updatePhoto(long userId, byte[] photoBytes) {
        if (photoBytes != null && photoBytes.length > MAX_PHOTO_BYTES) {
            return ServiceResult.failure("Photo must be smaller than 2 MB. Try a smaller image.");
        }
        try {
            authorizationService.requireSelfOrAdmin(userId);
            userDAO.updatePhoto(userId, photoBytes);
            return ServiceResult.success(photoBytes == null ? "Photo removed." : "Profile photo updated.", null);
        } catch (SQLException e) {
            return ServiceResult.failure("Photo could not be updated: " + e.getMessage());
        }
    }

    /** Never throws -- a missing/unreadable photo should fall back to the initials avatar, not break the profile screen. */
    public Optional<byte[]> loadPhoto(long userId) {
        try {
            return userDAO.findPhoto(userId);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }
}
