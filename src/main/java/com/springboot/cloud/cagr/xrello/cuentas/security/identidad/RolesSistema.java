package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRolSistemaRepository;

/**
 * PUNTO UNICO de resolucion de los roles de SISTEMA ({@code SOPORTE}, {@code AUDITOR}): se leen de
 * las tablas locales ({@code cta_usuario_rol_sistema}) por el id del usuario local ya resuelto,
 * nunca del token.
 *
 * <p><b>Es el punto de cambio</b> si la fuente de estos roles se mueve (por ejemplo, a
 * ms-usuario cuando el ecosistema defina roles por aplicacion): se reemplaza SOLO el cuerpo de
 * {@link #autoridadesDe}; ni {@code SecurityConfig}, ni los {@code @PreAuthorize}, ni
 * {@code UsuarioActual} se enteran. El ecosistema NO se ha comprometido a que ms-usuario sea esa
 * fuente — hoy el emisor tampoco publica el claim {@code roles}.</p>
 *
 * <p>Roles de PROYECTO (OWNER/ADMIN/MIEMBRO/OBSERVADOR) no pasan por aqui: se resuelven por
 * peticion en {@code AccesoProyecto}.</p>
 */
@Component
public class RolesSistema {

    private static final String PREFIJO = "ROLE_";

    private final UsuarioRolSistemaRepository usuarioRolSistemaRepository;

    public RolesSistema(UsuarioRolSistemaRepository usuarioRolSistemaRepository) {
        this.usuarioRolSistemaRepository = usuarioRolSistemaRepository;
    }

    public List<GrantedAuthority> autoridadesDe(Long usuarioId) {
        return usuarioRolSistemaRepository.findCodigosRolByUsuarioId(usuarioId).stream()
                .<GrantedAuthority>map(codigo -> new SimpleGrantedAuthority(PREFIJO + codigo))
                .toList();
    }
}
