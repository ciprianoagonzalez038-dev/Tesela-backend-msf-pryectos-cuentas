package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

/**
 * Usuario LOCAL de Xrello ya resuelto — el principal del {@code SecurityContext}. {@code usuarioId}
 * es el id de {@code cta_usuario}; NUNCA se deriva de un claim del token (ver
 * {@link ResolutorIdentidad}).
 *
 * @param aprovisionadoEnEstaPeticion {@code true} si esta misma peticion creo la cuenta local
 *        (aprovisionamiento just-in-time). Lo usa la aceptacion de invitaciones EMAIL_DIRIGIDO: un
 *        correo sin verificar no puede consumir una invitacion dirigida en el mismo flujo en que se
 *        creo la cuenta (ver XRELLO_CONTRATO.md, "email no verificado").
 */
public record IdentidadUsuario(Long usuarioId, String email, String nombre, boolean aprovisionadoEnEstaPeticion) {
}
