package com.bloodlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilTest {
    @Test
    void acceptsBangladeshiMobileFormats() {
        assertTrue(ValidationUtil.isValidPhone("01712345678"));
        assertTrue(ValidationUtil.isValidPhone("+8801712345678"));
        assertTrue(ValidationUtil.isValidPhone("8801712345678"));
    }

    @Test
    void rejectsInvalidEmailAndPhone() {
        assertFalse(ValidationUtil.isValidEmail("missing-at-sign"));
        assertFalse(ValidationUtil.isValidPhone("012345"));
    }

    @Test
    void strongPasswordPassesAllRules() {
        assertTrue(ValidationUtil.validatePassword("Blood123").isEmpty());
    }

    @Test
    void weakPasswordReturnsEveryMissingRequirement() {
        assertEquals(3, ValidationUtil.validatePassword("short").size());
    }
}
