package com.bloodlink.service;

import com.bloodlink.dao.UserDAO;
import com.bloodlink.model.*;
import com.bloodlink.util.PasswordUtil;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

public final class AuthService {
    private final UserDAO userDAO = new UserDAO();

    public ServiceResult<User> login(String email, String password, Role selectedRole) {
        if (!ValidationUtil.isValidEmail(email) || ValidationUtil.isBlank(password))
            return ServiceResult.failure("Enter a valid email address and password.");
        try {
            Optional<User> optionalUser = userDAO.findByEmail(email);
            if (optionalUser.isEmpty()) return ServiceResult.failure("No account was found for this email.");
            User user = optionalUser.get();
            if (user.getRole() != selectedRole) return ServiceResult.failure("The selected role does not match this account.");
            if (!user.isActive()) return ServiceResult.failure("This account is suspended. Contact an administrator.");

            if (!PasswordUtil.verify(password, userDAO.findPasswordHash(user.getId())))
                return ServiceResult.failure("The password is incorrect.");
            return ServiceResult.success("Welcome back, " + user.getFullName() + ".", user);
        } catch (Exception e) {
            return ServiceResult.failure("The database could not be reached. Check MySQL and your DB settings.");
        }
    }

    public ServiceResult<Long> register(RegistrationData data, String confirmPassword) {
        String validation = validateRegistration(data, confirmPassword);
        if (validation != null) return ServiceResult.failure(validation);
        try {
            if (userDAO.emailExists(data.email())) return ServiceResult.failure("An account already uses this email address.");
            long id = userDAO.register(data, PasswordUtil.hash(data.password()));
            String message = data.role() == Role.DONOR
                    ? "Donor account created. An administrator must approve it before you can receive requests."
                    : "Requester account created. You may sign in now.";
            return ServiceResult.success(message, id);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) return ServiceResult.failure("This email address is already registered.");
            return ServiceResult.failure("Registration failed: " + e.getMessage());
        } catch (Exception e) {
            return ServiceResult.failure("Registration failed: " + e.getMessage());
        }
    }

    private String validateRegistration(RegistrationData data, String confirmPassword) {
        if (data.role() == null || data.role() == Role.ADMIN) return "Choose Donor or Requester.";
        if (ValidationUtil.isBlank(data.fullName()) || data.fullName().trim().length() < 3) return "Enter your full name.";
        if (!ValidationUtil.isValidEmail(data.email())) return "Enter a valid email address.";
        if (!ValidationUtil.isValidPhone(data.phone())) return "Enter a valid Bangladeshi mobile number.";
        if (ValidationUtil.isBlank(data.district())) return "District is required.";
        List<String> passwordErrors = ValidationUtil.validatePassword(data.password());
        if (!passwordErrors.isEmpty()) return String.join("\n", passwordErrors);
        if (!data.password().equals(confirmPassword)) return "Password confirmation does not match.";
        if (data.role() == Role.DONOR) {
            if (data.bloodGroup() == null || data.birthDate() == null || data.weightKg() == null)
                return "Blood group, birth date, and weight are required for donors.";
            if (data.birthDate().isAfter(LocalDate.now()) || Period.between(data.birthDate(), LocalDate.now()).getYears() < 18)
                return "Donors must be at least 18 years old.";
            if (data.weightKg() < 35 || data.weightKg() > 250) return "Enter a realistic weight between 35 and 250 kg.";
            if (data.lastDonationDate() != null && data.lastDonationDate().isAfter(LocalDate.now()))
                return "Last donation date cannot be in the future.";
        }
        return null;
    }
}
