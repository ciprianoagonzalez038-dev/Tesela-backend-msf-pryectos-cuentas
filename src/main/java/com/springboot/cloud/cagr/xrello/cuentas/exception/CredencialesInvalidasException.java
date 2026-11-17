package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Email/password incorrectos, o un refresh token que no existe o ya expiro.
 * Mensaje SIEMPRE generico (nunca distingue "email no existe" de "password
 * incorrecta", ni "no existe" de "expirado") — evita filtrar que direcciones
 * de correo estan registradas. Mapeada a 401 por {@link CuentasExceptionHandler}.
 */
public class CredencialesInvalidasException extends CuentasException {

    public CredencialesInvalidasException(String message) {
        super(message);
    }
}
