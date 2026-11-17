package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionCreadaResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;

/**
 * Clase APARTE de {@link InvitacionFlujoTest} a proposito: {@link MockitoSpyBean} reemplaza
 * {@link ProyectoService} por un spy en TODO el contexto de Spring de esta clase — mezclarlo con
 * el resto de los tests de invitaciones (que dependen del {@code ProyectoService} real)
 * contaminaria su contexto compartido. Un contexto nuevo por clase es el costo normal de usar
 * {@code @MockitoSpyBean}, no un problema a evitar.
 *
 * <p>Simula el escenario que {@link InvitacionFlujoTest} no puede forzar con datos reales: un
 * fallo TECNICO (no un {@link com.springboot.cloud.cagr.xrello.cuentas.exception.CuentasException})
 * a mitad de {@code InvitacionService#aceptarInvitacion}, forzado con la forma menos invasiva
 * disponible — un spy sobre un punto real de la cadena, no una clase de produccion nueva solo
 * para el test.</p>
 *
 * <p><b>Cambio de comportamiento (D28/Tesela):</b> antes de centralizar la identidad en
 * ms-usuario, {@code RegistroService.registrar} era {@code @Transactional}: la creacion del
 * usuario local y la aceptacion de la invitacion corrian en la MISMA transaccion, asi que un
 * fallo tecnico en cualquiera de las dos revertia TODO. Ahora la creacion de la identidad
 * (ms-usuario + {@code AprovisionamientoUsuarioService}) usa su PROPIA transaccion, que debe
 * CERRARSE del todo antes de volver (ver el Javadoc de {@code resolverOAprovisionar} — necesita
 * poder releer tras un {@code DataIntegrityViolationException} concurrente). Esto significa que,
 * para cuando {@code aceptarInvitacion} corre, el usuario YA esta committeado — un fallo tecnico
 * ahi solo revierte la invitacion (uso, membresia), nunca el alta del usuario. Es el mismo
 * principio que ya aplicaba a un fallo de NEGOCIO (invitacion invalida/agotada): el registro
 * nunca se pierde, solo la invitacion deja de aplicarse — ahora tambien es cierto para un fallo
 * tecnico, con la diferencia de que la peticion SI responde 500 (el error es real e inesperado,
 * no se enmascara como "invitacion no aplicada").</p>
 */
@DisplayName("Registro con invitacion — fallo TECNICO durante la aceptacion (no de negocio)")
class RegistroConInvitacionFalloTecnicoTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @MockitoSpyBean
    private ProyectoService proyectoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Test
    @DisplayName("Un fallo tecnico durante aceptarInvitacion responde 500 y revierte el uso de la invitacion, "
            + "pero el usuario (ya creado en ms-usuario antes de aceptar) persiste")
    void falloTecnicoDuranteAceptacionRevierteSoloLaInvitacion() {
        TokenResponse owner = registrar("owner-fallo-tecnico@test.com", "Owner Fallo Tecnico");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Fallo Tecnico", "FTC");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        // agregarMiembro corre DESPUES de que ocuparUso ya incremento usos_actuales -- forzar el
        // fallo aqui es justo el punto donde, sin NESTED, el incremento habria quedado
        // persistido junto con la invitacion pese a que nunca se aplico de verdad.
        doThrow(new RuntimeException("Fallo tecnico simulado"))
                .when(proyectoService).agregarMiembro(anyLong(), anyLong(), anyString(), any());

        ResponseEntity<String> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "invitado-fallo-tecnico@test.com", "Invitado Fallo Tecnico", creada.token())),
                String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // El usuario YA estaba committeado (identidad en ms-usuario + aprovisionamiento local, su
        // propia transaccion) antes de intentar aceptar la invitacion — persiste pese al fallo.
        assertThat(usuarioRepository.findByEmail("invitado-fallo-tecnico@test.com")).isPresent();
        // La invitacion SI revierte: aceptarInvitacion es su propia transaccion, con el fallo
        // tecnico dentro de ella (no en noRollbackFor).
        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);
    }

    // ── Helpers (mismo patron que InvitacionFlujoTest) ──────────────────────────

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

    private String crearProyecto(String accessToken, String nombre, String prefijo) {
        ResponseEntity<ApiResponse<ProyectoResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos"), HttpMethod.POST,
                new HttpEntity<>(new CrearProyectoRequest(nombre, null, prefijo, null), authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<ProyectoResponse>>() { });
        return respuesta.getBody().getData().codigo();
    }

    private InvitacionCreadaResponse crearInvitacion(String accessToken, String codigoProyecto, CrearInvitacionRequest request) {
        ResponseEntity<ApiResponse<InvitacionCreadaResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos/" + codigoProyecto + "/invitaciones"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(accessToken)),
                new ParameterizedTypeReference<ApiResponse<InvitacionCreadaResponse>>() { });
        return respuesta.getBody().getData();
    }

    private org.springframework.http.HttpHeaders authHeaders(String accessToken) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
