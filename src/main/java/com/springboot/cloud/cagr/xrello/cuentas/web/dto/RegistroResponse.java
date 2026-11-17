package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

/**
 * Sin tokens: el registro ya no deja sesion iniciada (la cuenta no tiene clave usable todavia,
 * ver {@code RegistroRequest}) — el primer login real ocurre al verificar el correo
 * ({@code VerificarCorreoResponse}). {@code motivoInvitacionNoAplicada} es {@code null} cuando
 * {@code invitacionAplicada == true} o cuando no se mando ningun token — nunca aborta el registro
 * por una invitacion invalida (ver {@code security.RegistroService}).
 */
public record RegistroResponse(
        UsuarioResponse usuario,
        boolean invitacionAplicada,
        String motivoInvitacionNoAplicada) {
}
