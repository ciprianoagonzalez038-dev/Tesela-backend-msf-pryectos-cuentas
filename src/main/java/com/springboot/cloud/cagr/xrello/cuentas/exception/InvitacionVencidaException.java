package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * La invitacion ya no esta disponible: expirada o revocada (ver
 * {@link InvitacionAgotadaException} para el caso especifico de usos
 * agotados, mismo codigo HTTP). Mapeada a 410 GONE por
 * {@link CuentasExceptionHandler} — la invitacion existio, ya no sirve.
 */
public class InvitacionVencidaException extends CuentasException {

    public InvitacionVencidaException(String message) {
        super(message);
    }
}
