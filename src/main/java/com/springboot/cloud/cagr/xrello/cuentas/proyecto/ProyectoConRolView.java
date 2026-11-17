package com.springboot.cloud.cagr.xrello.cuentas.proyecto;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;

/**
 * Proyeccion de {@code listarProyectosDeUsuario}: un {@link Proyecto} junto
 * con el rol que tiene el usuario consultado en el, en UNA sola consulta
 * (ver {@code repository.MiembroProyectoRepository#findProyectosConRolByUsuarioId}
 * — sin N+1).
 */
public record ProyectoConRolView(Proyecto proyecto, String rolCodigo, boolean esOwner) {
}
