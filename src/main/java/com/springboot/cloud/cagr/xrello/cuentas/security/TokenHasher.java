package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genera tokens opacos (refresh tokens, tokens de invitacion) y los hashea
 * para almacenamiento — el valor en claro NUNCA se persiste, solo el hash
 * (ver XRELLO_CONTRATO.md sobre {@code cta_refresh_token}/{@code cta_invitacion}).
 */
public final class TokenHasher {

    private static final int LONGITUD_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenHasher() {
    }

    /** 32 bytes aleatorios (SecureRandom), Base64 URL-safe sin padding. */
    public static String generarTokenAleatorio() {
        byte[] bytes = new byte[LONGITUD_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal (64 caracteres) — lo unico que se persiste. */
    public static String sha256Hex(String valorEnClaro) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valorEnClaro.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}
