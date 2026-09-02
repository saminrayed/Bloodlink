package com.bloodlink.service;

import com.bloodlink.dao.NotificationDAO;
import com.bloodlink.model.Notification;

import java.sql.SQLException;
import java.util.List;

public final class NotificationService {
    private final NotificationDAO dao = new NotificationDAO();
    public List<Notification> list(long userId) throws SQLException { return dao.findByUser(userId, 100); }
    public long unreadCount(long userId) throws SQLException { return dao.unreadCount(userId); }
    public void markRead(long notificationId, long userId) throws SQLException { dao.markRead(notificationId, userId); }
    public void markAllRead(long userId) throws SQLException { dao.markAllRead(userId); }
}
