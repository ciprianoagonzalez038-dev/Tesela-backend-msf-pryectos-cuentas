package com.springboot.cloud.cagr.xrello.cuentas.web.admin.dto;

/** Resultado de recalcular {@code miembros_actuales} contra el conteo real de miembros. */
public record ReconciliacionResponse(int miembrosActuales) {
}
