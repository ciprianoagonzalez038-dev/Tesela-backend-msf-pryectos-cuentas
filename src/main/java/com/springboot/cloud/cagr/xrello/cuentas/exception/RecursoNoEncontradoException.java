package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * No existe el recurso solicitado (usuario, proyecto, rol, membresia, o un
 * limite sin configurar — ver {@link LimiteNoConfiguradoException}). Mapeada
 * a 404 por {@link CuentasExceptionHandler}.
 */
public class RecursoNoEncontradoException extends CuentasException {

    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
