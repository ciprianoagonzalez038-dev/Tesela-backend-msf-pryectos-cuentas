package com.springboot.cloud.cagr.xrello.cuentas.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.IdentidadUsuario;

/**
 * Expone el usuario LOCAL autenticado, ya resuelto por {@code security.identidad} al validar el
 * token — sin estado propio (singleton seguro). <b>No lee ni interpreta el token</b>: el principal
 * es un {@link IdentidadUsuario} y su id es el de {@code cta_usuario}, jamas un claim parseado.
 * {@code ArquitecturaTest} impide que cualquier clase fuera de {@code security.identidad} lea
 * claims de un {@code Jwt}, asi esa conversion no puede reintroducirse por otro camino.
 */
@Component
public class UsuarioActual {

    public Long getId() {
        return identidad().usuarioId();
    }

    public String getEmail() {
        return identidad().email();
    }

    public String getNombre() {
        return identidad().nombre();
    }

    /**
     * {@code true} si ESTA peticion creo la cuenta local (primer acceso de un usuario del
     * Authorization Server). Ver {@link IdentidadUsuario}.
     */
    public boolean isAprovisionadoEnEstaPeticion() {
        return identidad().aprovisionadoEnEstaPeticion();
    }

    private IdentidadUsuario identidad() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentidadUsuario identidad)) {
            throw new IllegalStateException(
                    "No hay un usuario autenticado en el contexto de seguridad — "
                            + "UsuarioActual solo se puede usar dentro de una peticion autenticada");
        }
        return identidad;
    }
}
