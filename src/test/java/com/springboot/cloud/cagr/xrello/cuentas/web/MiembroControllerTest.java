package com.springboot.cloud.cagr.xrello.cuentas.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.AceptarInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.AceptarInvitacionResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionCreadaResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.MiembroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

/**
 * {@code GET /api/v1/cuentas/proyectos/{codigo}/miembros} — en particular, que
 * {@code MiembroResponse#usuarioId} devuelva el id numérico REAL del usuario (comparado contra
 * {@link UsuarioRepository}, nunca expuesto por {@code UsuarioResponse}/{@code RegistroResponse}).
 * Agregado al extender la respuesta para que app-xrello-web pueda resolver {@code asignados}
 * (ids planos en {@code ms-xrello-tableros}) sin una llamada remota por tarjeta — ver
 * XRELLO_ESTADO_BACKEND.md, deuda conocida.
 */
@DisplayName("MiembroController — GET /miembros expone usuarioId (Testcontainers MySQL, HTTP real)")
class MiembroControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("El OWNER ve su propio usuarioId real, email, nombre y rol OWNER")
    void listarMiembrosDevuelveUsuarioIdRealDelOwner() {
        TokenResponse owner = registrar("owner-miembros@test.com", "Owner Miembros");
        String codigo = crearProyecto(owner.accessToken(), "Proyecto Miembros", "MIE").getBody().getData().codigo();
        Long ownerIdReal = usuarioRepository.findByEmail("owner-miembros@test.com").orElseThrow().getId();

        List<MiembroResponse> miembros = listarMiembros(codigo, owner.accessToken());

        assertThat(miembros).hasSize(1);
        MiembroResponse ownerMiembro = miembros.get(0);
        assertThat(ownerMiembro.usuarioId()).isEqualTo(ownerIdReal);
        assertThat(ownerMiembro.email()).isEqualTo("owner-miembros@test.com");
        assertThat(ownerMiembro.nombre()).isEqualTo("Owner Miembros");
        assertThat(ownerMiembro.rolCodigo()).isEqualTo(RolProyecto.ROL_OWNER);
        assertThat(ownerMiembro.esOwner()).isTrue();
    }

    @Test
    @DisplayName("Un MIEMBRO no-owner aparece con su propio usuarioId real y esOwner=false")
    void listarMiembrosDevuelveUsuarioIdRealDelMiembroNoOwner() {
        TokenResponse owner = registrar("owner-miembros2@test.com", "Owner Dos");
        String codigo = crearProyecto(owner.accessToken(), "Proyecto Miembros Dos", "MI2").getBody().getData().codigo();

        TokenResponse invitado = registrar("miembro-invitado@test.com", "Miembro Invitado");
        String token = crearInvitacion(codigo, owner.accessToken()).getBody().getData().token();
        aceptarInvitacion(token, invitado.accessToken());
        Long invitadoIdReal = usuarioRepository.findByEmail("miembro-invitado@test.com").orElseThrow().getId();

        List<MiembroResponse> miembros = listarMiembros(codigo, owner.accessToken());

        assertThat(miembros).hasSize(2);
        MiembroResponse miembro = miembros.stream()
                .filter(m -> m.email().equals("miembro-invitado@test.com"))
                .findFirst().orElseThrow();
        assertThat(miembro.usuarioId()).isEqualTo(invitadoIdReal);
        assertThat(miembro.rolCodigo()).isEqualTo(RolProyecto.ROL_MIEMBRO);
        assertThat(miembro.esOwner()).isFalse();
    }

    @Test
    @DisplayName("Un no-miembro recibe 403 — ver el id de un miembro exige ya poder ver su nombre/email")
    void noMiembroRecibe403() {
        TokenResponse owner = registrar("owner-miembros3@test.com", "Owner Tres");
        String codigo = crearProyecto(owner.accessToken(), "Proyecto Miembros Tres", "MI3").getBody().getData().codigo();
        TokenResponse ajeno = registrar("ajeno-miembros@test.com", "Ajeno");

        ResponseEntity<String> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos/" + codigo + "/miembros"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ajeno.accessToken())), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<MiembroResponse> listarMiembros(String codigo, String accessToken) {
        ResponseEntity<ApiResponse<List<MiembroResponse>>> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos/" + codigo + "/miembros"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<List<MiembroResponse>>>() { });
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().getData();
    }

    /**
     * Registra (sin clave, ver {@code RegistroRequest}) y de inmediato verifica el correo con la
     * clave de prueba de esta clase — por convencion del doble de prueba
     * ({@code StaticTestUsuarioDirectorioPort}), el "token" de verificacion es el propio correo.
     */
    private TokenResponse registrar(String email, String nombre) {
        restTemplate.exchange(url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(email, nombre, null)),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });
        ResponseEntity<ApiResponse<TokenResponse>> verificacion = restTemplate.exchange(
                url("/api/v1/publico/auth/verificar-correo"), HttpMethod.POST,
                new HttpEntity<>(new VerificarCorreoRequest(email, PASSWORD)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { });
        return verificacion.getBody().getData();
    }

    private ResponseEntity<ApiResponse<ProyectoResponse>> crearProyecto(String accessToken, String nombre, String prefijo) {
        CrearProyectoRequest request = new CrearProyectoRequest(nombre, null, prefijo, null);
        return restTemplate.exchange(url("/api/v1/cuentas/proyectos"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<ProyectoResponse>>() { });
    }

    private ResponseEntity<ApiResponse<InvitacionCreadaResponse>> crearInvitacion(String codigo, String accessToken) {
        CrearInvitacionRequest request = new CrearInvitacionRequest("ENLACE_ABIERTO", null, RolProyecto.ROL_MIEMBRO, 7, 5);
        return restTemplate.exchange(url("/api/v1/cuentas/proyectos/" + codigo + "/invitaciones"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<InvitacionCreadaResponse>>() { });
    }

    private ResponseEntity<ApiResponse<AceptarInvitacionResponse>> aceptarInvitacion(String token, String accessToken) {
        return restTemplate.exchange(url("/api/v1/cuentas/invitaciones/aceptar"), HttpMethod.POST,
                new HttpEntity<>(new AceptarInvitacionRequest(token), authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<AceptarInvitacionResponse>>() { });
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
