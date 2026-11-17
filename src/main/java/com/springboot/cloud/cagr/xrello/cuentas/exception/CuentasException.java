package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Base de todas las excepciones propias de ms-xrello-cuentas. Nunca se lanza
 * directamente; usar una de las subclases especificas. Capturada por
 * {@link CuentasExceptionHandler}.
 */
public abstract class CuentasException extends RuntimeException {

    protected CuentasException(String message) {
        super(message);
    }
}
