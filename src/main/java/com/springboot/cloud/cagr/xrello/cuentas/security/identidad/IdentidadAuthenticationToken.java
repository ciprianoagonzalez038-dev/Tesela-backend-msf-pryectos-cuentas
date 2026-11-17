package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Autenticacion ya resuelta contra la BD local: el principal es un {@link IdentidadUsuario} y las
 * autoridades son los roles de SISTEMA leidos de las tablas locales (ver {@link RolesSistema}).
 * Es lo UNICO que los converters de {@link EmisoresConfig} devuelven; el {@code Jwt} no sobrevive
 * a este paso, asi que ningun codigo posterior puede volver a leer claims.
 */
public class IdentidadAuthenticationToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final IdentidadUsuario identidad;

    public IdentidadAuthenticationToken(IdentidadUsuario identidad, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.identidad = identidad;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return identidad;
    }

    @Override
    public String getName() {
        return String.valueOf(identidad.usuarioId());
    }
}
