package com.springboot.cloud.cagr.xrello.cuentas.security;

/** Access token (JWT) + refresh token (opaco) emitidos juntos por login/refresh. */
public record TokenPair(String accessToken, String refreshToken) {
}
