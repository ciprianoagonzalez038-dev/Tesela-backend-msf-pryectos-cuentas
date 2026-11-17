package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * El {@code UPDATE} atomico de {@code InvitacionRepository#ocuparUso} afecto 0
 * filas — cubre tanto "ya alcanzo usos_maximos" como "estado != ACTIVA"
 * (revocada), sin distinguir cual (ver Javadoc de {@code ocuparUso}). Extiende
 * {@link InvitacionVencidaException}: mismo 410.
 */
public class InvitacionAgotadaException extends InvitacionVencidaException {

    public InvitacionAgotadaException(String message) {
        super(message);
    }
}
