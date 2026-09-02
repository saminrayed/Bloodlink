package com.bloodlink.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private PasswordUtil() { }
    public static String hash(String password) { return BCrypt.hashpw(password, BCrypt.gensalt(12)); }
    public static boolean verify(String password, String hash) {
        try { return hash != null && BCrypt.checkpw(password, hash); }
        catch (IllegalArgumentException e) { return false; }
    }
}
