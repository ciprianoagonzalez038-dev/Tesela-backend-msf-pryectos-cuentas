package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import java.time.LocalDateTime;

/**
 * {@code token} en claro — aparece SOLO en esta respuesta (la creacion de la invitacion) y en
 * ninguna otra parte del sistema; ni {@code InvitacionResumenResponse} ni ningun log lo llevan.
 */
public record InvitacionCreadaResponse(
        String codigo, String token, String tipo, String rolCodigo, LocalDateTime fechaExpiracion) {
}
