package com.springboot.cloud.cagr.xrello.cuentas.security;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;

/**
 * Resultado de {@code RegistroService#registrar}. Sin tokens: la cuenta no tiene clave usable
 * todavia (ver {@code RegistroRequest}), asi que no hay sesion que iniciar aqui — el primer login
 * real ocurre al verificar el correo. {@code motivoInvitacionNoAplicada} es {@code null} cuando
 * {@code invitacionAplicada == true} o cuando no se mando token.
 */
public record RegistroResultado(
        Usuario usuario,
        boolean invitacionAplicada,
        String motivoInvitacionNoAplicada) {
}
