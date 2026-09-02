package com.bloodlink.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ValidationUtil {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE = Pattern.compile("^(?:\\+?88)?01[3-9]\\d{8}$");

    private ValidationUtil() { }

    public static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    public static boolean isValidEmail(String value) { return value != null && EMAIL.matcher(value.trim()).matches(); }
    public static boolean isValidPhone(String value) { return value != null && PHONE.matcher(value.replace(" ", "")).matches(); }

    public static List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < 8) errors.add("Password must contain at least 8 characters.");
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) errors.add("Password needs an uppercase letter.");
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) errors.add("Password needs a lowercase letter.");
        if (password == null || password.chars().noneMatch(Character::isDigit)) errors.add("Password needs a number.");
        return errors;
    }
}
