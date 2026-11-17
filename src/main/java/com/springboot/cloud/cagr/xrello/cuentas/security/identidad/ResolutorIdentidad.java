package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

/**
 * UNICO lugar del servicio que lee claims de un {@link Jwt} (lo hace cumplir
 * {@code ArquitecturaTest}). Convierte un token YA VALIDADO (firma, emisor y vigencia los verifica
 * el decoder de su emisor, ver {@link EmisoresConfig}) en un {@link IdentidadAuthenticationToken}
 * con el usuario LOCAL.
 *
 * <p><b>Ningun claim se convierte jamas en el id local.</b> Cada emisor tiene su propia estrategia
 * y ninguna parsea {@code sub}:</p>
 * <ul>
 *   <li><b>Emisor externo (ms3-oauth):</b> {@code sub} es el subject del Authorization Server — en
 *       el ecosistema, el id numerico del usuario en ms-usuario, que PARSEA SIN ERROR como un
 *       Long y coincidiria con el id de OTRA persona en {@code cta_usuario}. Se trata siempre como
 *       texto opaco y se resuelve por {@code cta_usuario.subject_externo}
 *       ({@link AprovisionamientoUsuarioService}).</li>
 *   <li><b>Emisor local (TRANSITORIO):</b> el token lo firmo este mismo servicio con HS256 y su
 *       claim {@code email} es el de la cuenta, que no es editable; se resuelve por correo
 *       (UNIQUE), no por {@code sub}. Desaparece con el login local (XRELLO_CONTRATO.md §14).</li>
 * </ul>
 */
@Component
public class ResolutorIdentidad {

    private final UsuarioRepository usuarioRepository;
    private final AprovisionamientoUsuarioService aprovisionamiento;
    private final RolesSistema rolesSistema;

    public ResolutorIdentidad(
            UsuarioRepository usuarioRepository,
            AprovisionamientoUsuarioService aprovisionamiento,
            RolesSistema rolesSistema) {
        this.usuarioRepository = usuarioRepository;
        this.aprovisionamiento = aprovisionamiento;
        this.rolesSistema = rolesSistema;
    }

    // TRANSITORIO: migra al Authorization Server — se elimina con el emisor local.
    public AbstractAuthenticationToken autenticarTokenLocal(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new IdentidadRechazadaException(HttpStatus.UNAUTHORIZED, "Token sin correo");
        }
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IdentidadRechazadaException(HttpStatus.UNAUTHORIZED,
                        "El usuario del token ya no existe"));
        if (!Usuario.ESTADO_ACTIVO.equals(usuario.getEstado())) {
            throw new IdentidadRechazadaException(HttpStatus.FORBIDDEN, "La cuenta no esta activa");
        }
        return token(new IdentidadUsuario(usuario.getId(), usuario.getEmail(), usuario.getNombre(), false));
    }

    public AbstractAuthenticationToken autenticarTokenExterno(Jwt jwt) {
        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new IdentidadRechazadaException(HttpStatus.UNAUTHORIZED, "Token sin sub");
        }
        String nombre = jwt.getClaimAsString("nombre");
        if (!StringUtils.hasText(nombre)) {
            nombre = jwt.getClaimAsString("name");
        }
        boolean emailVerificado = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
        IdentidadUsuario identidad = aprovisionamiento.resolverOAprovisionar(
                subject, jwt.getClaimAsString("email"), nombre, emailVerificado);
        return token(identidad);
    }

    private IdentidadAuthenticationToken token(IdentidadUsuario identidad) {
        return new IdentidadAuthenticationToken(identidad, rolesSistema.autoridadesDe(identidad.usuarioId()));
    }
}
