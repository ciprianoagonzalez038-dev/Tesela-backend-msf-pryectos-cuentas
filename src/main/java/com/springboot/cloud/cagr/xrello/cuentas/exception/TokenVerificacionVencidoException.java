package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * El token del enlace de verificacion de correo (alta de cuenta) existio pero ya vencio o fue
 * usado. Mapeada a 410 GONE por {@link CuentasExceptionHandler} — mismo criterio que
 * {@link InvitacionVencidaException}, pero para el token de verificacion de correo, no de
 * invitacion a un proyecto.
 */
public class TokenVerificacionVencidoException extends CuentasException {

    public TokenVerificacionVencidoException(String message) {
        super(message);
    }
}
