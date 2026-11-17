package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionEmailNoVerificadoException;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.MiembroProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.security.EmisorExternoDePrueba;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.AceptarInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionCreadaResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

/**
 * Deuda abierta ligada a {@code email_verified} (XRELLO_CONTRATO.md): el ecosistema no verifica
 * correos, asi que una invitacion EMAIL_DIRIGIDO solo se acepta de un usuario que YA existia —
 * nunca de la cuenta creada en el mismo flujo (registro local o primer acceso del Authorization
 * Server). ENLACE_ABIERTO no cambia.
 */
@DisplayName("Invitaciones EMAIL_DIRIGIDO — correo no verificado (Testcontainers MySQL, HTTP real)")
class InvitacionEmailNoVerificadoTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @DynamicPropertySource
    static void emisorExterno(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", EmisorExternoDePrueba::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EmisorExternoDePrueba.ISSUER);
    }

    @Autowired
    private InvitacionService invitacionService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Autowired
    private MiembroProyectoRepository miembroProyectoRepository;

    @Test
    @DisplayName("servicio: una EMAIL_DIRIGIDO no la acepta una cuenta creada en el mismo flujo; una preexistente SI")
    void recienCreadoNoPreexistenteSi() {
        TokenResponse owner = registrar("owner-nv@test.com", "Owner");
        String proyecto = crearProyecto(owner.accessToken(), "Proyecto NV", "NVR");
        InvitacionCreadaResponse invitacion = crearInvitacion(owner.accessToken(), proyecto,
                new CrearInvitacionRequest(Invitacion.TIPO_EMAIL_DIRIGIDO, "destino-nv@test.com", "MIEMBRO", 7, 1));
        registrar("destino-nv@test.com", "Destino");
        Long idDestino = usuarioRepository.findByEmail("destino-nv@test.com").orElseThrow().getId();

        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(invitacion.token(), idDestino, true))
                .isInstanceOf(InvitacionEmailNoVerificadoException.class);

        // El mismo usuario, ya existente (flujo distinto): ahora si.
        assertThat(invitacionService.aceptarInvitacion(invitacion.token(), idDestino, false)).isNotNull();

        // Verificacion CONTRA BASE DE DATOS, no solo contra el objeto devuelto -- este camino pasa
        // por InvitacionRepository#ocuparUso (clearAutomatically = true, ver XRELLO_CONTRATO.md
        // §18); recargar por separado confirma que el uso y la membresia de verdad se persistieron.
        assertThat(invitacionRepository.findByCodigo(invitacion.codigo()).orElseThrow().getUsosActuales()).isEqualTo(1);
        assertThat(miembroProyectoRepository.findByProyecto_CodigoAndUsuario_Id(proyecto, idDestino)).isPresent();
    }

    @Test
    @DisplayName("servicio: una ENLACE_ABIERTO la acepta incluso una cuenta recien creada (no cambia)")
    void enlaceAbiertoNoCambia() {
        TokenResponse owner = registrar("owner-abierto-nv@test.com", "Owner");
        String proyecto = crearProyecto(owner.accessToken(), "Proyecto abierto NV", "ABN");
        InvitacionCreadaResponse invitacion = crearInvitacion(owner.accessToken(), proyecto,
                new CrearInvitacionRequest(Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5));
        registrar("nuevo-abierto-nv@test.com", "Nuevo");
        Long id = usuarioRepository.findByEmail("nuevo-abierto-nv@test.com").orElseThrow().getId();

        assertThat(invitacionService.aceptarInvitacion(invitacion.token(), id, true)).isNotNull();

        // Verificacion CONTRA BASE DE DATOS -- mismo motivo que en recienCreadoNoPreexistenteSi.
        assertThat(invitacionRepository.findByCodigo(invitacion.codigo()).orElseThrow().getUsosActuales()).isEqualTo(1);
        assertThat(miembroProyectoRepository.findByProyecto_CodigoAndUsuario_Id(proyecto, id)).isPresent();
    }

    @Test
    @DisplayName("registro local con EMAIL_DIRIGIDO a su propio correo: el registro completa pero la invitacion NO se aplica")
    void registroNoConsumeUnaDirigida() {
        TokenResponse owner = registrar("owner-reg-nv@test.com", "Owner");
        String proyecto = crearProyecto(owner.accessToken(), "Proyecto reg NV", "RGN");
        InvitacionCreadaResponse invitacion = crearInvitacion(owner.accessToken(), proyecto,
                new CrearInvitacionRequest(Invitacion.TIPO_EMAIL_DIRIGIDO, "suplantador@test.com", "MIEMBRO", 7, 1));

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest("suplantador@test.com", "Suplantador", invitacion.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody().getData().invitacionAplicada()).isFalse();
        assertThat(respuesta.getBody().getData().motivoInvitacionNoAplicada()).contains("correo");
        assertThat(usuarioRepository.findByEmail("suplantador@test.com")).isPresent();
    }

    @Test
    @DisplayName("HTTP con el Authorization Server: la peticion que aprovisiona la cuenta NO acepta la dirigida (403); la siguiente SI")
    void aprovisionamientoYAceptacionEnPeticionesDistintas() {
        TokenResponse owner = registrar("owner-http-nv@test.com", "Owner");
        String proyecto = crearProyecto(owner.accessToken(), "Proyecto HTTP NV", "HNV");
        InvitacionCreadaResponse invitacion = crearInvitacion(owner.accessToken(), proyecto,
                new CrearInvitacionRequest(Invitacion.TIPO_EMAIL_DIRIGIDO, "externo-nv@test.com", "MIEMBRO", 7, 1));
        String token = EmisorExternoDePrueba.token("sub-externo-nv", Map.of("email", "externo-nv@test.com", "nombre", "Externo"));

        ResponseEntity<String> primera = aceptar(token, invitacion.token());
        assertThat(primera.getStatusCode()).as("la cuenta se creo en ESTA peticion").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioRepository.findBySubjectExterno("sub-externo-nv")).as("la cuenta SI quedo creada").isPresent();

        ResponseEntity<String> segunda = aceptar(token, invitacion.token());
        assertThat(segunda.getStatusCode()).as("ya es un usuario existente").isEqualTo(HttpStatus.OK);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<String> aceptar(String accessToken, String tokenInvitacion) {
        return restTemplate.exchange(url("/api/v1/cuentas/invitaciones/aceptar"), HttpMethod.POST,
                new HttpEntity<>(new AceptarInvitacionRequest(tokenInvitacion), auth(accessToken)), String.class);
    }

    private HttpHeaders auth(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
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
        return restTemplate.exchange(url("/api/v1/publico/auth/verificar-correo"), HttpMethod.POST,
                new HttpEntity<>(new VerificarCorreoRequest(email, PASSWORD)),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() { }).getBody().getData();
    }

    private String crearProyecto(String accessToken, String nombre, String prefijo) {
        return restTemplate.exchange(url("/api/v1/cuentas/proyectos"), HttpMethod.POST,
                new HttpEntity<>(new CrearProyectoRequest(nombre, null, prefijo, null), auth(accessToken)),
                new ParameterizedTypeReference<ApiResponse<ProyectoResponse>>() { }).getBody().getData().codigo();
    }

    private InvitacionCreadaResponse crearInvitacion(String accessToken, String codigoProyecto, CrearInvitacionRequest request) {
        return restTemplate.exchange(url("/api/v1/cuentas/proyectos/" + codigoProyecto + "/invitaciones"), HttpMethod.POST,
                new HttpEntity<>(request, auth(accessToken)),
                new ParameterizedTypeReference<ApiResponse<InvitacionCreadaResponse>>() { }).getBody().getData();
    }
}
