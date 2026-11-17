package com.springboot.cloud.cagr.xrello.cuentas.exception;

import lombok.Getter;

/**
 * El proyecto ya alcanzo su cupo de {@code MIEMBROS_POR_PROYECTO} (resuelto
 * contra el plan del OWNER, ver {@code proyecto.ProyectoService#agregarMiembro}).
 * Mapeada a 409 por {@link CuentasExceptionHandler}: el estado actual del
 * recurso (proyecto lleno) entra en conflicto con la operacion pedida.
 */
@Getter
public class CupoProyectoLlenoException extends CuentasException {

    private final Long proyectoId;

    public CupoProyectoLlenoException(Long proyectoId) {
        super("El proyecto " + proyectoId + " no tiene cupo disponible para un miembro mas");
        this.proyectoId = proyectoId;
    }
}
