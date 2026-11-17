package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * El token del enlace de reseteo de clave existio pero ya vencio o fue usado. Mapeada a 410 GONE
 * por {@link CuentasExceptionHandler} — mismo criterio que {@link TokenVerificacionVencidoException},
 * pero para el token de reseteo de clave, no de verificacion de correo de alta.
 */
public class TokenReseteoClaveVencidoException extends CuentasException {

    public TokenReseteoClaveVencidoException(String message) {
        super(message);
    }
}
