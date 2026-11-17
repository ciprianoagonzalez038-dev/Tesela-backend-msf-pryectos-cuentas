package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Limite;
import com.springboot.cloud.cagr.xrello.cuentas.domain.LimiteUsuario;
import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CupoProyectoLlenoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionAgotadaException;
import com.springboot.cloud.cagr.xrello.cuentas.limite.CodigosLimite;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.LimiteRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.LimiteUsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.MiembroProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.AceptarInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearInvitacionRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.CrearProyectoRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionCreadaResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.InvitacionPreviewResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.ProyectoResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroRequest;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.RegistroResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.TokenResponse;
import com.springboot.cloud.cagr.xrello.cuentas.web.dto.VerificarCorreoRequest;

/**
 * Invitaciones de punta a punta — mezcla HTTP real (para los casos que ejercitan la cadena de
 * seguridad/serializacion) con llamadas directas a {@link InvitacionService} (para los casos de
 * concurrencia y verificacion de rollback, donde controlar el estado de la base es mas directo).
 */
@DisplayName("Invitaciones — flujo de punta a punta (Testcontainers MySQL)")
class InvitacionFlujoTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";

    @Autowired
    private InvitacionService invitacionService;

    @Autowired
    private ProyectoService proyectoService;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private LimiteRepository limiteRepository;

    @Autowired
    private LimiteUsuarioRepository limiteUsuarioRepository;

    @Autowired
    private MiembroProyectoRepository miembroProyectoRepository;

    @Autowired
    private com.springboot.cloud.cagr.xrello.cuentas.repository.PlanLimiteRepository planLimiteRepository;

    @Test
    @DisplayName("Una invitacion EMAIL_DIRIGIDO rechaza (403) a un usuario con un email distinto")
    void invitacionEmailDirigidoRechazaEmailDistinto() {
        TokenResponse owner = registrar("owner-email-dirigido@test.com", "Owner Email");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Email", "EML");

        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_EMAIL_DIRIGIDO, "destinatario-real@test.com", "MIEMBRO", 7, 1);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        TokenResponse otroUsuario = registrar("no-es-el-destinatario@test.com", "Otro Usuario");

        ResponseEntity<String> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/invitaciones/aceptar"), HttpMethod.POST,
                new HttpEntity<>(new AceptarInvitacionRequest(creada.token()), authHeaders(otroUsuario.accessToken())),
                String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Una invitacion expirada responde 404 en la consulta publica — igual que un token inexistente")
    void invitacionExpiradaResponde404IgualQueTokenInexistente() {
        // Unificado a proposito (ver XRELLO_CONTRATO.md §18): antes de este cambio, un token
        // vencido daba 410 y uno inexistente daba 404 -- dos respuestas distinguibles que
        // permitian confirmar si un token concreto alguna vez existio. Ahora ambos casos dan la
        // MISMA respuesta.
        TokenResponse owner = registrar("owner-expirada@test.com", "Owner Expirada");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Expirado", "EXP");

        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 1, 5);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        // Backdatea la expiracion directamente en la base (la API nunca permite crear una
        // invitacion ya vencida: diasVigencia es @Positive).
        Invitacion invitacion = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        invitacion.setFechaExpiracion(LocalDateTime.now().minusDays(1));
        invitacionRepository.save(invitacion);

        ResponseEntity<String> respuestaVencida = restTemplate.getForEntity(
                url("/api/v1/publico/invitaciones/" + creada.token()), String.class);
        ResponseEntity<String> respuestaInexistente = restTemplate.getForEntity(
                url("/api/v1/publico/invitaciones/token-que-nunca-existio"), String.class);

        assertThat(respuestaVencida.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuestaInexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Registro con un token de invitacion invalido completa el registro igual, con invitacionAplicada = false")
    void registroConTokenInvalidoCompletaIgual() {
        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "token-invalido@test.com", "Con Token Invalido", "esto-no-es-un-token-real")),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isFalse();
        assertThat(registro.motivoInvitacionNoAplicada()).isNotBlank();
        // El usuario SI quedo creado — el registro no se aborto.
        assertThat(usuarioRepository.findByEmail("token-invalido@test.com")).isPresent();
    }

    @Test
    @DisplayName("Registro con un token de invitacion VALIDO: el usuario queda creado Y miembro del "
            + "proyecto con el rol de la invitacion, usos_actuales se incrementa, invitacionAplicada = true")
    void registroConTokenValidoUneAlProyecto() {
        // Regresion del bug de REQUIRES_NEW: antes de este test, NINGUN test cubria el camino
        // principal (registro con una invitacion REALMENTE valida) -- el bug donde
        // aceptarInvitacion nunca veia al usuario recien creado (todavia sin commitear en la
        // transaccion REQUIRES_NEW separada) pasaba desapercibido porque los 34 tests existentes
        // o usaban tokens invalidos (completan igual, sin tocar el camino feliz) o llamaban
        // aceptarInvitacion direct con un usuario YA EXISTENTE (commiteado en una transaccion
        // previa, por lo que REQUIRES_NEW SI lo veia).
        TokenResponse owner = registrar("owner-token-valido@test.com", "Owner Valido");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Token Valido", "TKV");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "invitado-token-valido@test.com", "Invitado Valido", creada.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isTrue();
        assertThat(registro.motivoInvitacionNoAplicada()).isNull();

        Usuario invitado = usuarioRepository.findByEmail("invitado-token-valido@test.com").orElseThrow();
        // Fetch-joined (usuario/rolProyecto pre-cargados): findByProyecto_CodigoAndUsuario_Id
        // devuelve una entidad que queda detached apenas vuelve del repositorio (este test no
        // corre dentro de una transaccion propia) -- navegar su rolProyecto LAZY explotaria con
        // LazyInitializationException ("no session").
        MiembroProyecto miembro = miembroProyectoRepository.findByProyecto_CodigoFetchUsuarioYRol(codigoProyecto)
                .stream()
                .filter(m -> m.getUsuario().getId().equals(invitado.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El invitado no quedo como miembro del proyecto"));
        assertThat(miembro.getRolProyecto().getCodigo()).isEqualTo("MIEMBRO");

        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(1);
    }

    @Test
    @DisplayName("Registro con un token VENCIDO completa igual: usuario creado, invitacionAplicada = false, usos_actuales intacto")
    void registroConTokenVencidoCompletaIgualSinIncrementarUsos() {
        TokenResponse owner = registrar("owner-token-vencido@test.com", "Owner Vencido");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Token Vencido", "TKX");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 1, 5);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        Invitacion invitacion = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        invitacion.setFechaExpiracion(LocalDateTime.now().minusDays(1));
        invitacionRepository.save(invitacion);

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "invitado-token-vencido@test.com", "Invitado Vencido", creada.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isFalse();
        assertThat(registro.motivoInvitacionNoAplicada()).isNotBlank();
        assertThat(usuarioRepository.findByEmail("invitado-token-vencido@test.com")).isPresent();

        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);
    }

    @Test
    @DisplayName("Registro con un token AGOTADO completa igual: usuario creado, invitacionAplicada = false, usos_actuales sin crecer de nuevo")
    void registroConTokenAgotadoCompletaIgualSinIncrementarUsosDeNuevo() {
        TokenResponse owner = registrar("owner-token-agotado@test.com", "Owner Agotado");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Token Agotado", "TKA");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 1);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        // Agota el unico uso disponible con un usuario YA EXISTENTE (commiteado de antemano) --
        // confirma ademas que el camino de usuario EXISTENTE sigue funcionando tras el fix.
        Usuario primerInvitado = nuevoUsuario("primer-invitado-agotado@test.com");
        invitacionService.aceptarInvitacion(creada.token(), primerInvitado.getId(), false);
        assertThat(invitacionRepository.findByCodigo(creada.codigo()).orElseThrow().getUsosActuales()).isEqualTo(1);

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "invitado-token-agotado@test.com", "Invitado Agotado", creada.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isFalse();
        // El motivo es el mensaje claro de AGOTADA (lo que el frontend muestra tal cual al usuario).
        assertThat(registro.motivoInvitacionNoAplicada()).isEqualTo(InvitacionService.MENSAJE_INVITACION_AGOTADA);
        assertThat(usuarioRepository.findByEmail("invitado-token-agotado@test.com")).isPresent();

        // Sigue en 1 -- el intento fallido NO consumio un uso adicional.
        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(1);
    }

    @Test
    @DisplayName("Registro con un token REVOCADO completa igual: usuario creado, invitacionAplicada = false, usos_actuales intacto")
    void registroConTokenRevocadoCompletaIgualSinIncrementarUsos() {
        TokenResponse owner = registrar("owner-token-revocado@test.com", "Owner Revocado");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Token Revocado", "TKR");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        invitacionService.revocarInvitacion(codigoProyecto, creada.codigo());

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "invitado-token-revocado@test.com", "Invitado Revocado", creada.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isFalse();
        assertThat(registro.motivoInvitacionNoAplicada()).isNotBlank();
        assertThat(usuarioRepository.findByEmail("invitado-token-revocado@test.com")).isPresent();

        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);
    }

    @Test
    @DisplayName("Registro con una invitacion EMAIL_DIRIGIDO cuyo email NO coincide completa igual, sin aplicar la invitacion")
    void registroConEmailDirigidoQueNoCoincideCompletaIgual() {
        TokenResponse owner = registrar("owner-email-dirigido-registro@test.com", "Owner Email Registro");
        String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Email Registro", "EMR");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_EMAIL_DIRIGIDO, "destinatario-correcto@test.com", "MIEMBRO", 7, 1);
        InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "destinatario-equivocado@test.com", "Destinatario Equivocado", creada.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isFalse();
        assertThat(registro.motivoInvitacionNoAplicada()).isNotBlank();
        assertThat(usuarioRepository.findByEmail("destinatario-equivocado@test.com")).isPresent();

        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);
    }

    @Test
    @DisplayName("Registro con PROYECTOS_COMO_INVITADO agotado (0) completa igual, con invitacionAplicada = false")
    void registroConLimiteExcedidoCompletaIgual() {
        // Regresion especifica del reordenamiento de aceptarInvitacion (Tarea 1 del contrato):
        // LimiteExcedidoException ahora se dispara ANTES de ocuparUso, a traves de
        // LimiteService#verificarPuedeAceptarInvitacion -- que ya NO tiene su propio
        // @Transactional (ver su Javadoc). Sin la cobertura explicita de ESTA excepcion en el
        // noRollbackFor de aceptarInvitacion, este test fallaba con 500/UnexpectedRollbackException
        // (confirmado en caliente): acotar el noRollbackFor solo a CupoProyectoLlenoException
        // rompia este camino, porque la decision de "revertir o no" la toma el PRIMER boundary
        // @Transactional que la excepcion atraviesa -- aqui, el de aceptarInvitacion mismo.
        Limite limite = limiteRepository.findAll().stream()
                .filter(l -> CodigosLimite.PROYECTOS_COMO_INVITADO.equals(l.getCodigo()))
                .findFirst().orElseThrow();
        Plan free = planRepository.findByCodigo(Plan.CODIGO_FREE).orElseThrow();
        com.springboot.cloud.cagr.xrello.cuentas.domain.PlanLimite planLimite = planLimiteRepository.findAll().stream()
                .filter(pl -> pl.getPlan().getId().equals(free.getId()) && pl.getLimite().getId().equals(limite.getId()))
                .findFirst().orElseThrow();
        Integer original = planLimite.getValor();
        try {
            planLimite.setValor(0);
            planLimiteRepository.saveAndFlush(planLimite);

            TokenResponse owner = registrar("owner-limite-excedido@test.com", "Owner Limite");
            String codigoProyecto = crearProyecto(owner.accessToken(), "Proyecto Limite Excedido", "LEX");
            CrearInvitacionRequest request = new CrearInvitacionRequest(
                    Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5);
            InvitacionCreadaResponse creada = crearInvitacion(owner.accessToken(), codigoProyecto, request);

            ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                    url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                    new HttpEntity<>(new RegistroRequest(
                            "invitado-limite-excedido@test.com", "Invitado Limite", creada.token())),
                    new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            RegistroResponse registro = respuesta.getBody().getData();
            assertThat(registro.invitacionAplicada()).isFalse();
            assertThat(registro.motivoInvitacionNoAplicada()).contains("PROYECTOS_COMO_INVITADO");
            assertThat(usuarioRepository.findByEmail("invitado-limite-excedido@test.com")).isPresent();

            Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
            assertThat(recargada.getUsosActuales()).isEqualTo(0);
        } finally {
            planLimite.setValor(original);
            planLimiteRepository.saveAndFlush(planLimite);
        }
    }

    @Test
    @DisplayName("El mismo caso de cupo lleno, durante el registro de un usuario NUEVO: registro completa igual, usos_actuales y miembros_actuales intactos")
    void registroConProyectoLlenoCompletaIgualSinAlterarCupo() {
        TokenResponse ownerResp = registrar("owner-cupo-registro@test.com", "Owner Cupo Registro");
        Usuario owner = usuarioRepository.findByEmail("owner-cupo-registro@test.com").orElseThrow();
        limitarMiembrosPorProyecto(owner, 2);

        String codigoProyecto = crearProyecto(ownerResp.accessToken(), "Proyecto Cupo Registro", "PCR");
        CrearInvitacionRequest request = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5);
        InvitacionCreadaResponse creada = crearInvitacion(ownerResp.accessToken(), codigoProyecto, request);

        // Llena el cupo POR OTRA VIA (directo, no via esta invitacion) -- el proyecto llega a 2/2
        // DESPUES de creada la invitacion, igual que en aceptarConProyectoLlenoDejaUsosActualesIntacto.
        Usuario otroMiembro = nuevoUsuario("otro-miembro-cupo-registro@test.com");
        Long proyectoId = proyectoService.obtenerPorCodigo(codigoProyecto).getId();
        proyectoService.agregarMiembro(proyectoId, otroMiembro.getId(), "MIEMBRO", owner.getId());
        assertThat(proyectoService.obtenerPorCodigo(codigoProyecto).getMiembrosActuales()).isEqualTo(2);

        ResponseEntity<ApiResponse<RegistroResponse>> respuesta = restTemplate.exchange(
                url("/api/v1/publico/auth/registro"), HttpMethod.POST,
                new HttpEntity<>(new RegistroRequest(
                        "invitado-cupo-registro@test.com", "Invitado Cupo Registro", creada.token())),
                new ParameterizedTypeReference<ApiResponse<RegistroResponse>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegistroResponse registro = respuesta.getBody().getData();
        assertThat(registro.invitacionAplicada()).isFalse();
        assertThat(registro.motivoInvitacionNoAplicada()).isNotBlank();
        assertThat(usuarioRepository.findByEmail("invitado-cupo-registro@test.com")).isPresent();

        Invitacion recargada = invitacionRepository.findByCodigo(creada.codigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);

        Proyecto proyectoRecargado = proyectoService.obtenerPorCodigo(codigoProyecto);
        assertThat(proyectoRecargado.getMiembrosActuales()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un usuario que YA ES miembro acepta la invitacion otra vez: ni el uso ni el cupo se alteran")
    void aceptarInvitacionSiendoYaMiembroNoAlteraUsoNiCupo() {
        Usuario owner = nuevoUsuario("owner-ya-miembro@test.com");
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Proyecto Ya Miembro", null, "PYM");

        Usuario yaMiembro = nuevoUsuario("ya-miembro@test.com");
        proyectoService.agregarMiembro(proyecto.getId(), yaMiembro.getId(), "MIEMBRO", owner.getId());
        assertThat(proyectoService.obtenerPorCodigo(proyecto.getCodigo()).getMiembrosActuales()).isEqualTo(2);

        // Invitacion SEPARADA al mismo proyecto -- yaMiembro intenta aceptarla aunque ya es
        // miembro (ej. reusar un enlace viejo, o uno distinto al que uso la primera vez).
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5, owner.getId());

        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(creada.tokenEnClaro(), yaMiembro.getId(), false))
                .isInstanceOf(com.springboot.cloud.cagr.xrello.cuentas.exception.YaEsMiembroException.class);

        // Ni el uso de la invitacion ni el cupo del proyecto cambiaron -- el INSERT duplicado en
        // cta_miembro_proyecto (uq_cta_miembro_proyecto_proyecto_usuario) nunca llega a ocurrir,
        // asi que "miembro" sigue null en el catch de aceptarInvitacion y no hay nada que
        // decrementar; el flush fallido revierte por su cuenta lo demas (ver Javadoc).
        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);

        Proyecto proyectoRecargado = proyectoService.obtenerPorCodigo(proyecto.getCodigo());
        assertThat(proyectoRecargado.getMiembrosActuales()).isEqualTo(2);
    }

    @Test
    @DisplayName("Aceptar una invitacion con el proyecto lleno deja usos_actuales SIN incrementar (verifica el rollback)")
    void aceptarConProyectoLlenoDejaUsosActualesIntacto() {
        Usuario owner = nuevoUsuario("owner-lleno@test.com");
        // Override: cupo para 2 miembros -- alcanza para crear el proyecto (owner=1/2, hay cupo
        // libre) Y para que crearInvitacion pase su propia verificacion de cupo disponible.
        limitarMiembrosPorProyecto(owner, 2);

        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Proyecto Lleno", null, "LLN");
        assertThat(proyecto.getMiembrosActuales()).isEqualTo(1);

        // Invitacion creada MIENTRAS habia cupo (1/2) -- crearInvitacion la acepta sin problema.
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5, owner.getId());

        // Otro miembro ocupa el cupo restante POR OTRA VIA (directo, no via esta invitacion) --
        // el proyecto llega a 2/2 DESPUES de creada la invitacion.
        Usuario otroMiembro = nuevoUsuario("otro-miembro-llena-cupo@test.com");
        proyectoService.agregarMiembro(proyecto.getId(), otroMiembro.getId(), "MIEMBRO", owner.getId());
        assertThat(proyectoService.obtenerPorCodigo(proyecto.getCodigo()).getMiembrosActuales()).isEqualTo(2);

        // Ahora SI esta lleno: aceptar la invitacion (que todavia tiene usos disponibles) debe
        // fallar por cupo del PROYECTO, no de la invitacion.
        Usuario invitado = nuevoUsuario("invitado-proyecto-lleno@test.com");
        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(creada.tokenEnClaro(), invitado.getId(), false))
                .isInstanceOf(CupoProyectoLlenoException.class);

        // El UPDATE atomico de ocuparUso SI se aplico (usos_actuales paso a 1), pero
        // agregarMiembro fallo despues por cupo -- CupoProyectoLlenoException sobrevive via
        // noRollbackFor (ver Javadoc de aceptarInvitacion) y el catch deshace el uso a mano.
        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);

        // El invitado NO quedo como miembro del proyecto -- el contador sigue en 2 (owner + el
        // otro miembro), sin el invitado rechazado.
        Proyecto proyectoRecargado = proyectoService.obtenerPorCodigo(proyecto.getCodigo());
        assertThat(proyectoRecargado.getMiembrosActuales()).isEqualTo(2);
    }

    @Test
    @DisplayName("AGOTADA: el ULTIMO uso (y solo ese) pasa la invitacion de ACTIVA a AGOTADA")
    void elUltimoUsoMarcaLaInvitacionComoAgotada() {
        Usuario owner = nuevoUsuario("owner-agotada-ultimo-uso@test.com");
        limitarMiembrosPorProyecto(owner, 100);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Agotada Ultimo Uso", null, "AUU");
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 3, owner.getId());
        String codigo = creada.invitacion().getCodigo();

        // 1.er y 2.o uso: siguen ACTIVA (el UPDATE atomico compara contra el valor ANTERIOR de usos_actuales)
        invitacionService.aceptarInvitacion(creada.tokenEnClaro(), nuevoUsuario("agotada-u1@test.com").getId(), false);
        Invitacion trasPrimero = invitacionRepository.findByCodigo(codigo).orElseThrow();
        assertThat(trasPrimero.getUsosActuales()).isEqualTo(1);
        assertThat(trasPrimero.getEstado()).isEqualTo(Invitacion.ESTADO_ACTIVA);

        invitacionService.aceptarInvitacion(creada.tokenEnClaro(), nuevoUsuario("agotada-u2@test.com").getId(), false);
        Invitacion trasSegundo = invitacionRepository.findByCodigo(codigo).orElseThrow();
        assertThat(trasSegundo.getUsosActuales()).isEqualTo(2);
        assertThat(trasSegundo.getEstado()).isEqualTo(Invitacion.ESTADO_ACTIVA);

        // 3.er uso = usos_maximos: AGOTADA
        invitacionService.aceptarInvitacion(creada.tokenEnClaro(), nuevoUsuario("agotada-u3@test.com").getId(), false);
        Invitacion trasTercero = invitacionRepository.findByCodigo(codigo).orElseThrow();
        assertThat(trasTercero.getUsosActuales()).isEqualTo(3);
        assertThat(trasTercero.getEstado()).isEqualTo(Invitacion.ESTADO_AGOTADA);
    }

    @Test
    @DisplayName("AGOTADA: una invitacion de UN solo uso queda AGOTADA al aceptarla")
    void invitacionDeUnUsoQuedaAgotadaAlAceptar() {
        Usuario owner = nuevoUsuario("owner-agotada-un-uso@test.com");
        limitarMiembrosPorProyecto(owner, 100);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Agotada Un Uso", null, "AUN");
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "OBSERVADOR", 7, 1, owner.getId());

        invitacionService.aceptarInvitacion(creada.tokenEnClaro(), nuevoUsuario("agotada-unico@test.com").getId(), false);

        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(1);
        assertThat(recargada.getEstado()).isEqualTo(Invitacion.ESTADO_AGOTADA);
    }

    @Test
    @DisplayName("AGOTADA: aceptar una invitacion AGOTADA falla con un mensaje claro y no consume nada; la consulta publica sigue dando 404")
    void aceptarInvitacionAgotadaDaMensajeClaro() {
        Usuario owner = nuevoUsuario("owner-agotada-mensaje@test.com");
        limitarMiembrosPorProyecto(owner, 100);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Agotada Mensaje", null, "AGM");
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 1, owner.getId());
        invitacionService.aceptarInvitacion(creada.tokenEnClaro(), nuevoUsuario("agotada-m1@test.com").getId(), false);

        Usuario otro = nuevoUsuario("agotada-m2@test.com");
        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(creada.tokenEnClaro(), otro.getId(), false))
                .isInstanceOf(InvitacionAgotadaException.class)
                .hasMessage(InvitacionService.MENSAJE_INVITACION_AGOTADA);

        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(1);
        assertThat(recargada.getEstado()).isEqualTo(Invitacion.ESTADO_AGOTADA);

        // La consulta PUBLICA conserva el 404 unificado (XRELLO_CONTRATO.md §18): AGOTADA no se distingue de un token inexistente.
        ResponseEntity<String> preview = restTemplate.getForEntity(
                url("/api/v1/publico/invitaciones/" + creada.tokenEnClaro()), String.class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("AGOTADA: una fila anterior al estado (ACTIVA con usos_actuales = usos_maximos) tambien se rechaza con el mensaje claro")
    void filaHeredadaActivaConUsosCompletosSeTrataComoAgotada() {
        Usuario owner = nuevoUsuario("owner-agotada-heredada@test.com");
        limitarMiembrosPorProyecto(owner, 100);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Agotada Heredada", null, "AHE");
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 2, owner.getId());

        // Simula una fila de antes de que existiera AGOTADA: usos completos pero estado ACTIVA.
        Invitacion fila = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        fila.setUsosActuales(2);
        invitacionRepository.save(fila);

        Usuario otro = nuevoUsuario("agotada-heredada@test.com");
        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(creada.tokenEnClaro(), otro.getId(), false))
                .isInstanceOf(InvitacionAgotadaException.class)
                .hasMessage(InvitacionService.MENSAJE_INVITACION_AGOTADA);
        assertThat(invitacionRepository.findByCodigo(fila.getCodigo()).orElseThrow().getUsosActuales()).isEqualTo(2);
    }

    @Test
    @DisplayName("AGOTADA: si el ultimo uso se COMPENSA (proyecto lleno), la invitacion vuelve de AGOTADA a ACTIVA con usos_actuales = 0")
    void compensarElUltimoUsoDevuelveLaInvitacionAActiva() {
        Usuario owner = nuevoUsuario("owner-agotada-compensa@test.com");
        limitarMiembrosPorProyecto(owner, 2);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Agotada Compensa", null, "ACO");

        // Invitacion de UN uso, creada mientras habia cupo (1/2)...
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 1, owner.getId());
        // ...y el cupo se llena por otra via.
        Usuario otroMiembro = nuevoUsuario("agotada-compensa-llena-cupo@test.com");
        proyectoService.agregarMiembro(proyecto.getId(), otroMiembro.getId(), "MIEMBRO", owner.getId());

        Usuario invitado = nuevoUsuario("agotada-compensa-invitado@test.com");
        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(creada.tokenEnClaro(), invitado.getId(), false))
                .isInstanceOf(CupoProyectoLlenoException.class);

        // ocuparUso la dejo AGOTADA (1/1) y liberarUso la devolvio a ACTIVA (0/1): quedo usable otra vez.
        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);
        assertThat(recargada.getEstado()).isEqualTo(Invitacion.ESTADO_ACTIVA);
    }

    @Test
    @DisplayName("CONCURRENCIA: 10 hilos aceptando una invitacion de 3 usos -> exactamente 3 exitos")
    void concurrenciaAceptarInvitacionRespetaUsosMaximos() throws InterruptedException {
        Usuario owner = nuevoUsuario("owner-concurrencia-invitacion@test.com");
        // Cupo de miembros amplio -- el limite bajo prueba es el de la INVITACION (usosMaximos),
        // no el del proyecto.
        limitarMiembrosPorProyecto(owner, 100);

        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Concurrencia Invitacion", null, "CCI");
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 3, owner.getId());

        int hilos = 10;
        List<Usuario> candidatos = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            candidatos.add(nuevoUsuario("candidato-invitacion-" + i + "@test.com"));
        }

        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger agotada = new AtomicInteger();
        List<Throwable> inesperadas = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();

        for (Usuario candidato : candidatos) {
            futuros.add(executor.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    invitacionService.aceptarInvitacion(creada.tokenEnClaro(), candidato.getId(), false);
                    exitos.incrementAndGet();
                } catch (InvitacionAgotadaException ex) {
                    agotada.incrementAndGet();
                } catch (Throwable ex) {
                    inesperadas.add(ex);
                }
            }));
        }

        listos.await(10, TimeUnit.SECONDS);
        salida.countDown();
        for (Future<?> futuro : futuros) {
            try {
                futuro.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("Un hilo fallo inesperadamente", e);
            }
        }
        executor.shutdown();

        assertThat(inesperadas).isEmpty();
        assertThat(exitos.get()).isEqualTo(3);
        assertThat(agotada.get()).isEqualTo(7);

        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(3);
        // Ni siquiera con 10 hilos compitiendo se pierde la transicion: el ultimo uso deja AGOTADA.
        assertThat(recargada.getEstado()).isEqualTo(Invitacion.ESTADO_AGOTADA);

        Proyecto proyectoRecargado = proyectoService.obtenerPorCodigo(proyecto.getCodigo());
        assertThat(proyectoRecargado.getMiembrosActuales()).isEqualTo(4); // owner + 3 aceptados
    }

    @Test
    @DisplayName("CONCURRENCIA CRUZADA: 10 hilos, invitacion de 5 usos, proyecto con cupo para 3 invitados -> exactamente 3 exitos, ambos contadores consistentes")
    void concurrenciaAceptarInvitacionRespetaCupoDeProyectoYUsosDeInvitacion() throws InterruptedException {
        // El limite bajo prueba AHORA es el del PROYECTO (3 cupos libres: owner + 3 = 4), mas
        // estricto que el de la invitacion (5 usos) -- la Tarea 0 es justo la que garantiza que
        // un intento que SI reclamo un uso pero fallo DESPUES por cupo lleno libera ese uso, para
        // que otro candidato pueda tomarlo. Sin esa compensacion, usos_actuales podria quedar por
        // encima de 3 (hasta 5) aunque solo 3 personas se hayan unido de verdad.
        Usuario owner = nuevoUsuario("owner-concurrencia-cruzada@test.com");
        limitarMiembrosPorProyecto(owner, 4);

        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Concurrencia Cruzada", null, "CCZ");
        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5, owner.getId());

        int hilos = 10;
        List<Usuario> candidatos = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            candidatos.add(nuevoUsuario("candidato-cruzado-" + i + "@test.com"));
        }

        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger agotada = new AtomicInteger();
        AtomicInteger cupoLleno = new AtomicInteger();
        List<Throwable> inesperadas = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();

        for (Usuario candidato : candidatos) {
            futuros.add(executor.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    invitacionService.aceptarInvitacion(creada.tokenEnClaro(), candidato.getId(), false);
                    exitos.incrementAndGet();
                } catch (InvitacionAgotadaException ex) {
                    agotada.incrementAndGet();
                } catch (CupoProyectoLlenoException ex) {
                    cupoLleno.incrementAndGet();
                } catch (Throwable ex) {
                    inesperadas.add(ex);
                }
            }));
        }

        listos.await(10, TimeUnit.SECONDS);
        salida.countDown();
        for (Future<?> futuro : futuros) {
            try {
                futuro.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("Un hilo fallo inesperadamente", e);
            }
        }
        executor.shutdown();

        assertThat(inesperadas).isEmpty();
        // La mezcla exacta de "agotada" vs "cupo lleno" entre los 7 que fallan depende de que
        // tan rapido cada hilo compensa su uso liberado -- no se fija aqui (no es lo que importa:
        // ver el comentario de arriba), solo que entre los dos suman el resto.
        assertThat(exitos.get()).isEqualTo(3);
        assertThat(agotada.get() + cupoLleno.get()).isEqualTo(7);

        // El cruce de los DOS contadores -- lo que este test existe para probar (Tarea 0): sin la
        // compensacion del cupo, miembros_actuales podria haber quedado por encima de 4, y sin la
        // compensacion del uso (ya existente), usos_actuales podria haber quedado por encima de 3.
        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(3);

        Proyecto proyectoRecargado = proyectoService.obtenerPorCodigo(proyecto.getCodigo());
        assertThat(proyectoRecargado.getMiembrosActuales()).isEqualTo(4); // owner + 3 aceptados
    }

    @Test
    @DisplayName("Un OWNER del proyecto A no puede revocar una invitacion del proyecto B (404, y la invitacion de B queda intacta)")
    void revocarInvitacionDeOtroProyectoDa404YNoLaAfecta() {
        // Regresion del IDOR cross-proyecto en revocarInvitacion (ver XRELLO_CONTRATO.md §18,
        // "Identificador protegido vs identificador mutado"): el @PreAuthorize solo verificaba el
        // rol del caller sobre #codigo (el proyecto DE LA URL) -- antes del fix, el servicio
        // buscaba la invitacion GLOBALMENTE por su propio codigo, asi que un OWNER de CUALQUIER
        // proyecto podia revocar la invitacion de otro proyecto ajeno si conocia su UUID.
        TokenResponse ownerA = registrar("owner-a-revocar@test.com", "Owner A");
        String codigoA = crearProyecto(ownerA.accessToken(), "Proyecto A", "PRA");

        TokenResponse ownerB = registrar("owner-b-revocar@test.com", "Owner B");
        String codigoB = crearProyecto(ownerB.accessToken(), "Proyecto B", "PRB");
        CrearInvitacionRequest requestB = new CrearInvitacionRequest(
                Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5);
        InvitacionCreadaResponse invitacionB = crearInvitacion(ownerB.accessToken(), codigoB, requestB);

        // ownerA SI es OWNER de A (satisface el @PreAuthorize sobre #codigo=A), pero intenta
        // revocar la invitacion de B pasando el codigo de SU PROPIO proyecto en la URL.
        ResponseEntity<String> respuesta = restTemplate.exchange(
                url("/api/v1/cuentas/proyectos/" + codigoA + "/invitaciones/" + invitacionB.codigo()),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(ownerA.accessToken())), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Invitacion recargada = invitacionRepository.findByCodigo(invitacionB.codigo()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(Invitacion.ESTADO_ACTIVA);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private Usuario nuevoUsuario(String email) {
        Plan free = planRepository.findByCodigo(Plan.CODIGO_FREE).orElseThrow();
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash("hash-de-prueba");
        usuario.setNombre("Usuario de prueba");
        usuario.setEstado(Usuario.ESTADO_ACTIVO);
        usuario.setPlan(free);
        usuario.setEmailVerificado(false);
        return usuarioRepository.save(usuario);
    }

    /** Override de {@code MIEMBROS_POR_PROYECTO} SOLO para este usuario, via {@code cta_limite_usuario}. */
    private void limitarMiembrosPorProyecto(Usuario usuario, int valor) {
        Limite limite = limiteRepository.findAll().stream()
                .filter(l -> CodigosLimite.MIEMBROS_POR_PROYECTO.equals(l.getCodigo()))
                .findFirst()
                .orElseThrow();
        LimiteUsuario override = new LimiteUsuario();
        override.setUsuario(usuario);
        override.setLimite(limite);
        override.setValor(valor);
        limiteUsuarioRepository.save(override);
    }
}
