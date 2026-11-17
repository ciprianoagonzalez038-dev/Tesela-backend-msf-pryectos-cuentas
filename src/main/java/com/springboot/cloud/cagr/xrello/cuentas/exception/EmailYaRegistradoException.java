package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Ya existe una cuenta con ese email — viola {@code uq_cta_usuario_email}.
 * Mapeada a 409 por {@link CuentasExceptionHandler}.
 */
public class EmailYaRegistradoException extends CuentasException {

    public EmailYaRegistradoException(String email) {
        super("Ya existe una cuenta registrada con el email " + email);
    }
}
