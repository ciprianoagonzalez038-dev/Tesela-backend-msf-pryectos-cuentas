package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Se intento usar un refresh token que ya habia sido usado/rotado antes —
 * indica un robo de token (o una carrera de doble uso). Revoca TODAS las
 * sesiones del usuario (ver {@code security.RefreshTokenService#rotar}).
 * Mapeada a 401 por {@link CuentasExceptionHandler} (mismo codigo que
 * {@link CredencialesInvalidasException}: desde el punto de vista del
 * cliente, el resultado es identico — hay que autenticarse de nuevo).
 */
public class ReusoRefreshTokenException extends CuentasException {

    public ReusoRefreshTokenException(String message) {
        super(message);
    }
}
