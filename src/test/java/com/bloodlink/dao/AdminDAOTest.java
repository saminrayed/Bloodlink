package com.bloodlink.dao;

import com.bloodlink.model.AdminUserRow;
import com.bloodlink.model.DashboardStats;
import com.bloodlink.model.DemandRow;
import com.bloodlink.util.DatabaseSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminDAOTest {

    @BeforeAll
    static void setup() {
        DatabaseSetup.ensureInitialized();
    }

    @Test
    void testLoadStatsAndDemandRows() throws SQLException {
        AdminDAO adminDAO = new AdminDAO();
        DashboardStats stats = adminDAO.loadStats();
        assertNotNull(stats);
        assertTrue(stats.totalDonors() >= 0);

        List<DemandRow> demandRows = adminDAO.demandRows();
        assertNotNull(demandRows);
        assertFalse(demandRows.isEmpty());

        List<AdminUserRow> users = adminDAO.findUsers("");
        assertNotNull(users);
        assertFalse(users.isEmpty());

        var monthly = adminDAO.monthlyRequests(6);
        assertNotNull(monthly);
        assertEquals(6, monthly.size());
    }
}
