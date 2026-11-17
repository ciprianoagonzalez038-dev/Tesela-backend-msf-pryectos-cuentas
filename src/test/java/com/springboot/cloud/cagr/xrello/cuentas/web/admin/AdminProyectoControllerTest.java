package com.springboot.cloud.cagr.xrello.cuentas.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolSistema;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.domain.UsuarioRolSistema;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RolSistemaRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRolSistemaRepository;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.admin.dto.ReconciliacionResponse;

/**
 * {@code /api/v1/admin/proyectos/{codigo}/reconciliar-contador} — protegida por el rol de
 * SISTEMA {@code ROLE_SOPORTE} (ver {@code security.SecurityConfig}), no por {@code esOwner} —
 * ver Tarea 3 de la migracion a roles de sistema.
 */
@DisplayName("AdminProyectoController — proteccion por rol de sistema SOPORTE (Testcontainers MySQL, HTTP real)")
class AdminProyectoControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolSistemaRepository rolSistemaRepository;

    @Autowired
    private UsuarioRolSistemaRepository usuarioRolSistemaRepository;

    @Test
    @DisplayName("Un OWNER sin rol SOPORTE recibe 403 al reconciliar el contador de SU PROPIO proyecto")
    void ownerSinSoporteRecibe403() {
        TokenResponse owner = registrarYVerificar("owner-sin-soporte@test.com", "Owner sin soporte");
        String codigo = crearProyecto(owner.accessToken(), "Proyecto sin soporte", "SIN")
                .getBody().getData().codigo();

        ResponseEntity<String> respuesta = restTemplate.exchange(
                url("/api/v1/admin/proyectos/" + codigo + "/reconciliar-contador"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(owner.accessToken())), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Un usuario con rol de sistema SOPORTE recibe 200 al reconciliar el contador, aunque no sea miembro del proyecto")
    void usuarioConSoporteRecibe200() {
        TokenResponse owner = registrarYVerificar("owner-con-soporte@test.com", "Owner con soporte");
        String codigo = crearProyecto(owner.accessToken(), "Proyecto con soporte", "CON")
                .getBody().getData().codigo();

        RegistroResponse soporte = registrar("soporte@test.com", "Agente de soporte").getBody().getData();
        asignarRolSistema(soporte, RolSistema.ROL_SOPORTE);
        // El rol se asigna DESPUES de registrar (sin sesion todavia, ver RegistroRequest) -> la
        // verificacion de correo (primera y unica sesion real) ya sale con el claim actualizado.
        TokenResponse soporteConRol = verificarCorreo("soporte@test.com");

        ResponseEntity<ApiResponse<ReconciliacionResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/admin/proyectos/" + codigo + "/reconciliar-contador"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(soporteConRol.accessToken())),
                new ParameterizedTypeReference<ApiResponse<ReconciliacionResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().getData().miembrosActuales()).isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void asignarRolSistema(RegistroResponse registro, String codigoRol) {
        Usuario usuario = usuarioRepository.findByEmail(registro.usuario().email()).orElseThrow();
        RolSistema rol = rolSistemaRepository.findByCodigo(codigoRol).orElseThrow();
        UsuarioRolSistema asignacion = new UsuarioRolSistema();
        asignacion.setUsuario(usuario);
        asignacion.setRolSistema(rol);
        usuarioRolSistemaRepository.save(asignacion);
    }

    /**
     * Verifica el correo con la clave de prueba de esta clase — por convencion del doble de
     * prueba ({@code StaticTestUsuarioDirectorioPort}), el "token" de verificacion es el propio
     * correo. Es el primer momento en que la cuenta tiene sesion real (ver {@code RegistroRequest}).
     */
    private TokenResponse verificarCorreo(String email) {
        ResponseEntity<ApiResponse<TokenResponse>> verificacion = restTemplate.exchange(
                url("/api/v1/publico/auth/verificar-correo"), HttpMethod.POST,
                new HttpEntity<>(new VerificarCorreoRequest(email, PASSWORD)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { });
        return verificacion.getBody().getData();
    }

    /** Registra (sin clave, ver {@code RegistroRequest}) y de inmediato verifica el correo. */
    private TokenResponse registrarYVerificar(String email, String nombre) {
        registrar(email, nombre);
        return verificarCorreo(email);
    }

    private ResponseEntity<ApiResponse<RegistroResponse>> registrar(String email, String nombre) {
        RegistroRequest request = new RegistroRequest(email, nombre, null);
        return restTemplate.exchange(url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });
    }

    private ResponseEntity<ApiResponse<ProyectoResponse>> crearProyecto(String accessToken, String nombre, String prefijo) {
        CrearProyectoRequest request = new CrearProyectoRequest(nombre, null, prefijo, null);
        return restTemplate.exchange(url("/api/v1/cuentas/proyectos"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<ProyectoResponse>>() { });
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
