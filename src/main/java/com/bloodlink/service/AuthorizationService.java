package com.bloodlink.service;

import com.bloodlink.dao.UserDAO;
import com.bloodlink.model.Role;
import com.bloodlink.model.User;
import com.bloodlink.util.SessionManager;

import java.sql.SQLException;

/**
 * Server-side authorization checks that do not trust the caller's claimed
 * identity or the fact that only an admin-only screen currently calls a
 * given method. A UI hiding a button is not authorization -- every service
 * method that performs a privileged action must verify the actor's role
 * itself, here, before touching the database.
 * <p>
 * Today every caller of {@code requireAdmin} already happens to be gated by
 * the login screen only routing Admin accounts to the admin dashboard. This
 * check is defense in depth: it closes the gap so that stays true even if a
 * future caller, a bug elsewhere, or a test harness invokes a privileged
 * service method directly.
 */
public final class AuthorizationService {
    private final UserDAO userDAO = new UserDAO();

    public void requireAdmin(long actorId) throws SQLException {
        User actor = userDAO.findById(actorId)
                .orElseThrow(() -> new SQLException("Unauthorized: account not found."));
        if (actor.getRole() != Role.ADMIN || !actor.isActive()) {
            throw new SQLException("Unauthorized: administrator privileges are required for this action.");
        }
    }

    /**
     * For self-service methods (a donor updating their own availability, health
     * info, or reference hospital): verifies the account being modified is the one
     * actually logged in for this session (or an admin), rather than trusting a
     * bare id parameter that today only ever happens to equal the caller's own id
     * by construction of the current controllers. Checking the live session here,
     * not just comparing two ids, means this stays correct even if a future
     * controller bug or a different call path passes the wrong id.
     */
    public void requireSelfOrAdmin(long targetUserId) throws SQLException {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null) throw new SQLException("Unauthorized: no active session.");
        if (current.getId() == targetUserId) return;
        if (current.getRole() == Role.ADMIN) {
            requireAdmin(current.getId());
            return;
        }
        throw new SQLException("Unauthorized: you can only modify your own account.");
    }
}
