package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * La operacion pedida viola una regla de autorizacion/invariante del dominio
 * (ej. asignar rol OWNER directamente via {@code agregarMiembro} en vez de
 * {@code transferirPropiedad}). Mapeada a 403 por
 * {@link CuentasExceptionHandler}.
 */
public class SinPermisoException extends CuentasException {

    public SinPermisoException(String message) {
        super(message);
    }
}
