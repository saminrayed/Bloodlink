package com.bloodlink.service;

import com.bloodlink.dao.UserDAO;
import com.bloodlink.util.PasswordUtil;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;

public final class AdminService {
    private final UserDAO userDAO = new UserDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    public ServiceResult<Void> setApproved(long userId, boolean approved, long adminId) {
        try {
            authorizationService.requireAdmin(adminId);
            userDAO.setApproved(userId, approved, adminId);
            return ServiceResult.success(approved ? "User approved." : "Approval revoked.", null);
        } catch (SQLException e) { return ServiceResult.failure(e.getMessage()); }
    }

    public ServiceResult<Void> setActive(long userId, boolean active, long adminId) {
        try {
            authorizationService.requireAdmin(adminId);
            userDAO.setActive(userId, active, adminId);
            return ServiceResult.success(active ? "User activated." : "User suspended.", null);
        } catch (SQLException e) { return ServiceResult.failure(e.getMessage()); }
    }

    public ServiceResult<Void> resetPassword(long userId, String newPassword, long adminId) {
        List<String> errors = ValidationUtil.validatePassword(newPassword);
        if (!errors.isEmpty()) return ServiceResult.failure(String.join("\n", errors));
        try {
            authorizationService.requireAdmin(adminId);
            userDAO.resetPassword(userId, PasswordUtil.hash(newPassword), adminId);
            return ServiceResult.success("Password reset completed.", null);
        } catch (SQLException e) { return ServiceResult.failure(e.getMessage()); }
    }
}
