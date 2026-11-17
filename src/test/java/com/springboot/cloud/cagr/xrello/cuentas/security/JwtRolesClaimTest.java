package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolSistema;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.domain.UsuarioRolSistema;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RolSistemaRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRolSistemaRepository;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

import java.util.List;

/**
 * El JWT emitido por {@link TokenService} debe incluir los roles de SISTEMA del usuario (claim
 * {@code roles}) y NUNCA roles de proyecto (esos se resuelven en cada request via
 * {@link AccesoProyecto}, jamas viajan en el token) — ver XRELLO_CONTRATO.md.
 */
@DisplayName("JWT — claim roles (Testcontainers MySQL, HTTP real)")
class JwtRolesClaimTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolSistemaRepository rolSistemaRepository;

    @Autowired
    private UsuarioRolSistemaRepository usuarioRolSistemaRepository;

    @Test
    @DisplayName("Un usuario sin roles de sistema recibe un token con roles vacio")
    void usuarioSinRolesDeSistemaRecibeRolesVacio() {
        TokenResponse registro = registrarYVerificar("sin-roles-sistema@test.com", "Sin roles");

        Jwt jwt = jwtDecoder.decode(registro.accessToken());

        List<String> roles = jwt.getClaimAsStringList("roles");
        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("Un usuario con rol de sistema SOPORTE recibe el claim roles=[SOPORTE]; ningun rol de proyecto viaja en el token")
    void usuarioConRolDeSistemaRecibeClaimRoles() {
        registrarYVerificar("con-rol-sistema@test.com", "Con rol de sistema");
        Usuario usuario = usuarioRepository.findByEmail("con-rol-sistema@test.com").orElseThrow();
        RolSistema soporte = rolSistemaRepository.findByCodigo(RolSistema.ROL_SOPORTE).orElseThrow();
        UsuarioRolSistema asignacion = new UsuarioRolSistema();
        asignacion.setUsuario(usuario);
        asignacion.setRolSistema(soporte);
        usuarioRolSistemaRepository.save(asignacion);

        // El token de la verificacion ya se emitio antes de asignar el rol -> login de nuevo
        // (la cuenta ya esta activa, con la clave fijada al verificar) para obtener uno con el
        // claim actualizado.
        ResponseEntity<ApiResponse<com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse>> login =
                restTemplate.exchange(url("/api/v1/publico/auth/login"), HttpMethod.POST,
                        new HttpEntity<>(new com.springboot.cloud.cagr.xrello.cuentas.web.dto.LoginRequest(
                                "con-rol-sistema@test.com", PASSWORD)),
                        new ParameterizedTypeReference<ApiResponse<com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse>>() { });
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        Jwt jwt = jwtDecoder.decode(login.getBody().getData().accessToken());

        assertThat(jwt.getClaimAsStringList("roles")).containsExactly(RolSistema.ROL_SOPORTE);
        // Ningun rol de PROYECTO (OWNER/ADMIN/MIEMBRO/OBSERVADOR) aparece nunca como claim —
        // "roles" es exclusivamente de sistema; no existe ningun otro claim de roles en el token.
        assertThat(jwt.getClaims()).containsKey("roles");
        assertThat(jwt.getClaims().keySet())
                .noneMatch(clave -> clave.toLowerCase().contains("proyecto"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Registra (sin clave, ver {@code RegistroRequest}) y de inmediato verifica el correo con la
     * clave de prueba de esta clase — por convencion del doble de prueba
     * ({@code StaticTestUsuarioDirectorioPort}), el "token" de verificacion es el propio correo.
     */
    private TokenResponse registrarYVerificar(String email, String nombre) {
        restTemplate.exchange(url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(email, nombre, null)),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });
        return restTemplate.exchange(url("/api/v1/publico/auth/verificar-correo"), HttpMethod.POST,
                new HttpEntity<>(new VerificarCorreoRequest(email, PASSWORD)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { }).getBody().getData();
    }
}
