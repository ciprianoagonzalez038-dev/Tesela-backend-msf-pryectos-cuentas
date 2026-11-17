package com.springboot.cloud.cagr.xrello.cuentas.exception;

/**
 * Un ADMIN (u OWNER) intento expulsar al OWNER del proyecto via
 * {@code proyecto.ProyectoService#removerMiembro} — el owner solo deja de serlo transfiriendo la
 * propiedad ({@code transferirPropiedad}), nunca siendo removido. Mapeada a 409 (a diferencia de
 * {@link SinPermisoException}/403: no es que el llamador no tenga permiso de expulsar miembros —
 * lo tiene — sino que el ESTADO del recurso, "este usuario es el owner", entra en conflicto con
 * la operacion pedida).
 */
public class NoSePuedeExpulsarOwnerException extends CuentasException {

    public NoSePuedeExpulsarOwnerException(Long proyectoId) {
        super("El usuario es el owner del proyecto " + proyectoId
                + "; no se le puede expulsar, usar transferirPropiedad primero");
    }
}
