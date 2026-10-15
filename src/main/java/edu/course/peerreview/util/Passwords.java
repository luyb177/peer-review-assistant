package edu.course.peerreview.util;

import edu.course.peerreview.service.BusinessException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

public final class Passwords {
    private static final int ITERATIONS = 210_000;
    private Passwords() { }
    public static String hash(char[] password) {
        if (password.length < 8 || password.length > 128) throw new BusinessException("密码长度须为 8—128 个字符。");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean verify(char[] password, String encoded) {
        try {
            String[] parts = encoded.split(":");
            int rounds = Integer.parseInt(parts[0]);
            if (rounds < 10_000 || rounds > 1_000_000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]), derive(password, Base64.getDecoder().decode(parts[1]), rounds));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("PBKDF2 unavailable", e); }
        finally { spec.clearPassword(); }
    }
}
