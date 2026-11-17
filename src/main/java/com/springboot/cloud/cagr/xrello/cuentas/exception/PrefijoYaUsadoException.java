package com.springboot.cloud.cagr.xrello.cuentas.exception;

import lombok.Getter;

/**
 * El propietario ya tiene otro proyecto con ese prefijo — violacion de {@code uq_cta_proyecto_owner_prefijo},
 * traducida desde {@code DataIntegrityViolationException} en {@code proyecto.ProyectoService#crearProyecto}
 * (BUG-E2E-021: antes de este handler, el 500 generico de {@code GlobalExceptionHandler} se mostraba en vez
 * de un mensaje claro). Mapeada a 409 por {@link CuentasExceptionHandler} — mismo patron que
 * {@link YaEsMiembroException} para la constraint de membresia.
 */
@Getter
public class PrefijoYaUsadoException extends CuentasException {

    private final String prefijo;

    public PrefijoYaUsadoException(String prefijo) {
        super("Ya tienes un proyecto con el prefijo " + prefijo + ". Elige otro.");
        this.prefijo = prefijo;
    }
}
