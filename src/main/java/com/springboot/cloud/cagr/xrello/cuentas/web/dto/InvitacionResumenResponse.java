package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import java.time.LocalDateTime;

/** Listado de invitaciones de un proyecto — NUNCA lleva {@code tokenHash}. */
public record InvitacionResumenResponse(
        String codigo,
        String tipo,
        String emailDestino,
        String rolCodigo,
        String estado,
        int usosMaximos,
        int usosActuales,
        LocalDateTime fechaExpiracion) {
}
