package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResumenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RefreshRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

import java.util.List;

/**
 * Flujo de autenticacion de punta a punta, sobre HTTP real (ver
 * {@code AbstractIntegrationTest}, {@code webEnvironment = RANDOM_PORT}) — la
 * cadena de filtros de Spring Security no se puede ejercitar de verdad con
 * llamadas directas a los servicios.
 */
@DisplayName("Autenticacion — flujo de punta a punta (Testcontainers MySQL, HTTP real)")
class AuthFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @Test
    @DisplayName("Registro + verificacion de correo + acceso a un endpoint protegido; sin token, 401")
    void registroLoginYAccesoProtegido() {
        String email = "flujo-completo@test.com";

        TokenResponse registro = registrarYVerificar(email, "Flujo Completo", null);
        assertThat(registro.accessToken()).isNotBlank();
        assertThat(registro.refreshToken()).isNotBlank();

        ResponseEntity<ApiResponse<TokenResponse>> loginResp = login(email, PASSWORD);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = loginResp.getBody().getData().accessToken();

        ResponseEntity<ApiResponse<List<ProyectoResumenResponse>>> home = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<List<ProyectoResumenResponse>>>() { });
        assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> sinToken = restTemplate.getForEntity(url("/api/v1/cuentas/proyectos"), String.class);
        assertThat(sinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Refresh rota el token; reusar uno ya revocado revoca TODAS las sesiones y da 401")
    void refreshRotaYReusoRevocaTodasLasSesiones() {
        String email = "flujo-refresh@test.com";
        TokenResponse registro = registrarYVerificar(email, "Flujo Refresh", null);
        String refreshToken1 = registro.refreshToken();

        ResponseEntity<ApiResponse<TokenResponse>> refresh1 = refresh(refreshToken1);
        assertThat(refresh1.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshToken2 = refresh1.getBody().getData().refreshToken();
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        // Reusar refreshToken1 (ya usado/revocado) -> 401, deteccion de reuso.
        ResponseEntity<String> reuso = restTemplate.postForEntity(
                url("/api/v1/publico/auth/refresh"), new RefreshRequest(refreshToken1), String.class);
        assertThat(reuso.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // refreshToken2 (que SI era valido) tambien quedo revocado por la deteccion de reuso.
        ResponseEntity<String> intentoConSegundo = restTemplate.postForEntity(
                url("/api/v1/publico/auth/refresh"), new RefreshRequest(refreshToken2), String.class);
        assertThat(intentoConSegundo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Un usuario que no es miembro de un proyecto recibe 403 al intentar verlo")
    void noMiembroRecibe403() {
        TokenResponse ownerA = registrarYVerificar("owner-a-403@test.com", "Owner A", null);
        ResponseEntity<ApiResponse<ProyectoResponse>> proyecto = crearProyecto(
                ownerA.accessToken(), "Proyecto de A", "AAX");
        assertThat(proyecto.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String codigo = proyecto.getBody().getData().codigo();

        TokenResponse usuarioB = registrarYVerificar("usuario-b-403@test.com", "Usuario B", null);

        ResponseEntity<String> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos/" + codigo), HttpMethod.GET,
                new HttpEntity<>(authHeaders(usuarioB.accessToken())), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // El propio owner SI puede verlo.
        ResponseEntity<ApiResponse<ProyectoResponse>> comoOwner = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos/" + codigo), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerA.accessToken())),
                new ParameterizedTypeReference<ApiResponse<ProyectoResponse>>() { });
        assertThat(comoOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Registra (sin clave, ver {@code RegistroRequest}) y de inmediato verifica el correo con la
     * clave de prueba de esta clase — por convencion del doble de prueba
     * ({@code StaticTestUsuarioDirectorioPort}), el "token" de verificacion es el propio correo.
     */
    private TokenResponse registrarYVerificar(String email, String nombre, String tokenInvitacion) {
        restTemplate.exchange(url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(email, nombre, tokenInvitacion)),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });
        ResponseEntity<ApiResponse<TokenResponse>> verificacion = restTemplate.exchange(
                url("/api/v1/publico/auth/verificar-correo"), HttpMethod.POST,
                new HttpEntity<>(new VerificarCorreoRequest(email, PASSWORD)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { });
        return verificacion.getBody().getData();
    }

    private ResponseEntity<ApiResponse<TokenResponse>> login(String email, String password) {
        return restTemplate.exchange(url("/api/v1/publico/auth/login"), HttpMethod.POST,
                new HttpEntity<>(new com.springboot.cloud.cagr.xrello.cuentas.web.dto.LoginRequest(email, password)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { });
    }

    private ResponseEntity<ApiResponse<TokenResponse>> refresh(String refreshToken) {
        return restTemplate.exchange(url("/api/v1/publico/auth/refresh"), HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(refreshToken)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { });
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
