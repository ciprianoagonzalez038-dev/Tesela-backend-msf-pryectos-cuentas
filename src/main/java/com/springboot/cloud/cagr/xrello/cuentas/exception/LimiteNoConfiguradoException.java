package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * No hay ningun valor resuelto para un limite (ni override en
 * {@code cta_limite_usuario} ni valor del plan en {@code cta_plan_limite}).
 * No deberia ocurrir en operacion normal — todo plan tiene fila en
 * {@code cta_plan_limite} para los 4 codigos sembrados en
 * {@code V2__datos_semilla_cuentas.sql}; si aparece, es una configuracion de
 * plan/limite incompleta.
 */
public class LimiteNoConfiguradoException extends RecursoNoEncontradoException {

    public LimiteNoConfiguradoException(Long usuarioId, String codigoLimite) {
        super("No hay limite '" + codigoLimite + "' configurado (ni override de usuario ni del plan) "
                + "para el usuario " + usuarioId);
    }
}
