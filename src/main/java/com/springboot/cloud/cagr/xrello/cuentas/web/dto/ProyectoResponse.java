package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

/** Expone el {@code codigo} UUID, nunca el id numerico interno. */
public record ProyectoResponse(
        String codigo, String nombre, String descripcion, String prefijo, String estado, int miembrosActuales) {
}
