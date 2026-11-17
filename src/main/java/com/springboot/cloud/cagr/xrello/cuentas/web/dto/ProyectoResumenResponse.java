package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

/** Fila del Home ({@code GET /api/v1/cuentas/proyectos}) — proyecto + rol del usuario en el. */
public record ProyectoResumenResponse(String codigo, String nombre, String rolCodigo, boolean esOwner) {
}
