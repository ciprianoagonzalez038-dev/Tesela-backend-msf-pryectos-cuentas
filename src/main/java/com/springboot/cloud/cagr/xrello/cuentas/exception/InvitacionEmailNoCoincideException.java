package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Una invitacion {@code EMAIL_DIRIGIDO} fue aceptada por un usuario cuyo email
 * no coincide con {@code emailDestino}. Extiende {@link SinPermisoException}
 * (403): la invitacion es valida, este usuario en particular no puede usarla.
 */
public class InvitacionEmailNoCoincideException extends SinPermisoException {

    public InvitacionEmailNoCoincideException(String message) {
        super(message);
    }
}
