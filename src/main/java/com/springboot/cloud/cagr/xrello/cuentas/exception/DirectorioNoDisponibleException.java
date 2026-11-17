package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * ms-usuario (directorio de identidades) no respondió a tiempo o no está disponible. Sigue siendo una
 * {@link IllegalStateException} (la autenticación y el alta fallan cerrados), pero
 * {@link CuentasExceptionHandler} la mapea a 503 en vez del 500 genérico.
 */
public class DirectorioNoDisponibleException extends IllegalStateException {

    public DirectorioNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
