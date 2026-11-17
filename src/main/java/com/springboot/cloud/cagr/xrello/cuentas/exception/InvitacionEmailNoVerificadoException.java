package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Una invitacion {@code EMAIL_DIRIGIDO} no se acepta de una cuenta creada en el MISMO flujo: el
 * ecosistema todavia no verifica correos, y quien registre el correo de otra persona podria
 * consumir la invitacion que se le envio a ella. Se acepta solo de un usuario que YA existia.
 * 403, igual que {@link InvitacionEmailNoCoincideException}. Deuda abierta ligada a
 * {@code email_verified} (XRELLO_CONTRATO.md): cuando el emisor lo emita, se levanta.
 */
public class InvitacionEmailNoVerificadoException extends SinPermisoException {

    public InvitacionEmailNoVerificadoException(String message) {
        super(message);
    }
}
