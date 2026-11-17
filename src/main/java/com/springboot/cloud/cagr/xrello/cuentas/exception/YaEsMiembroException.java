package com.springboot.cloud.cagr.xrello.cuentas.exception;

import lombok.Getter;

/**
 * El usuario ya es miembro del proyecto — violacion de
 * {@code uq_cta_miembro_proyecto_proyecto_usuario}, traducida desde
 * {@code DataIntegrityViolationException} en
 * {@code proyecto.ProyectoService#agregarMiembro}. Mapeada a 409 por
 * {@link CuentasExceptionHandler}.
 */
@Getter
public class YaEsMiembroException extends CuentasException {

    private final Long proyectoId;
    private final Long usuarioId;

    public YaEsMiembroException(Long proyectoId, Long usuarioId) {
        super("El usuario " + usuarioId + " ya es miembro del proyecto " + proyectoId);
        this.proyectoId = proyectoId;
        this.usuarioId = usuarioId;
    }
}
