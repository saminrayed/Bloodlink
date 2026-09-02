package com.bloodlink.util;

import com.bloodlink.model.User;

public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    private User currentUser;
    private SessionManager() { }
    public static SessionManager getInstance() { return INSTANCE; }
    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }
    public void clear() { currentUser = null; }
    public boolean isAuthenticated() { return currentUser != null; }
}
