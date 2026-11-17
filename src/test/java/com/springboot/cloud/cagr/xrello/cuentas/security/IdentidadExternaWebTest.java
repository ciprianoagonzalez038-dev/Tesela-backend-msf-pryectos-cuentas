package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.cloud.cagr.commons.dto.ApiResponse;
import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolSistema;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.domain.UsuarioRolSistema;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RolSistemaRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRolSistemaRepository;
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
 * Cadena de seguridad REAL con un emisor externo (JWKS por HTTP, RS256) conviviendo con el emisor
 * local (registro/login propios, que siguen en pie).
 *
 * <p>El caso critico: {@code sub} del Authorization Server = id numerico del usuario en
 * ms-usuario, que PARSEA SIN ERROR como el id de otra persona en {@code cta_usuario}. Con el
 * {@code Long.valueOf(sub)} anterior, un token externo con {@code sub="7"} habria entrado como
 * el usuario local 7 (suplantacion silenciosa). Ahora el {@code sub} solo se resuelve por
 * {@code subject_externo}.</p>
 */
@DisplayName("Identidad externa — el sub NO es el id local; aprovisionamiento y roles (HTTP real)")
class IdentidadExternaWebTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PasswordSeguro123!";
    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void emisorExterno(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", EmisorExternoDePrueba::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EmisorExternoDePrueba.ISSUER);
    }

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository planRepository;

    @Autowired
    private RolSistemaRepository rolSistemaRepository;

    @Autowired
    private UsuarioRolSistemaRepository usuarioRolSistemaRepository;

    @Autowired
    private ProyectoService proyectoService;

    @Test
    @DisplayName("sub=\"<id local de otra persona>\" NO entra como ella: se aprovisiona un usuario NUEVO y no ve sus proyectos")
    void subNumericoNoSuplantaAlUsuarioLocal() throws Exception {
        TokenResponse victima = registrar("victima-sub@test.com", "Victima");
        crearProyecto(victima.accessToken(), "Proyecto de la victima", "VIC");
        Long idVictima = usuarioRepository.findByEmail("victima-sub@test.com").orElseThrow().getId();
        // Desde D28/Tesela, registrar() YA vincula subject_externo (el id de ms-usuario) — no es
        // null como antes de la migracion. Se captura para comprobar que el ataque no lo toca.
        String subjectExternoVictimaAntes = usuarioRepository.findById(idVictima).orElseThrow().getSubjectExterno();

        // El mismo numero que el id local de la victima, pero emitido por el Authorization Server.
        String tokenExterno = EmisorExternoDePrueba.token(String.valueOf(idVictima),
                Map.of("email", "atacante-sub@test.com", "nombre", "Otra Persona"));

        ResponseEntity<String> proyectos = restTemplate.exchange(url("/api/v1/cuentas/proyectos"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenExterno)), String.class);

        assertThat(proyectos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(proyectos.getBody()).path("data")).as("no ve los proyectos de la victima").isEmpty();
        Usuario aprovisionado = usuarioRepository.findBySubjectExterno(String.valueOf(idVictima)).orElseThrow();
        assertThat(aprovisionado.getId()).as("cuenta nueva, NO la de la victima").isNotEqualTo(idVictima);
        assertThat(aprovisionado.getEmail()).isEqualTo("atacante-sub@test.com");
        // y la victima sigue siendo ella, intacta: su propio subject_externo (el id de ms-usuario) no cambio.
        assertThat(usuarioRepository.findById(idVictima).orElseThrow().getSubjectExterno())
                .isEqualTo(subjectExternoVictimaAntes);
    }

    @Test
    @DisplayName("primer token de un sub nuevo crea la cuenta (FREE, sin password, ACTIVO); el segundo reutiliza la misma")
    void aprovisionaYReutiliza() {
        String sub = "sub-nuevo-http";
        String token = EmisorExternoDePrueba.token(sub, Map.of("email", "Nuevo.Http@Test.com", "nombre", "Nuevo Http"));

        assertThat(get("/api/v1/cuentas/proyectos", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/cuentas/proyectos", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        Usuario u = usuarioRepository.findBySubjectExterno(sub).orElseThrow();
        assertThat(u.getEmail()).isEqualTo("nuevo.http@test.com");
        assertThat(u.getPasswordHash()).isNull();
        assertThat(u.getEstado()).isEqualTo(Usuario.ESTADO_ACTIVO);
        assertThat(u.getPlan().getId()).isEqualTo(planRepository.findByCodigo("FREE").orElseThrow().getId());
        assertThat(usuarioRepository.findAll().stream().filter(x -> sub.equals(x.getSubjectExterno()))).hasSize(1);
    }

    @Test
    @DisplayName("un token sin correo da 403 con mensaje claro y NO crea ninguna cuenta")
    void sinCorreoNoCrea() throws Exception {
        String sub = "sub-sin-correo";
        long antes = usuarioRepository.count();

        ResponseEntity<String> r = get("/api/v1/cuentas/proyectos", EmisorExternoDePrueba.token(sub, Map.of("nombre", "Sin Correo")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(JSON.readTree(r.getBody()).path("message").asText()).contains("correo");
        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(usuarioRepository.findBySubjectExterno(sub)).isEmpty();
    }

    @Test
    @DisplayName("un correo que ya es de una cuenta local NO se vincula por un token sin email_verified (409): evita heredar la cuenta ajena")
    void noVinculaPorCorreoSinVerificar() {
        registrar("local-existente@test.com", "Local Existente");
        long antes = usuarioRepository.count();
        // Desde D28/Tesela, registrar() YA vincula subject_externo (el id de ms-usuario) — se
        // captura para comprobar que el intento sin verificar no lo pisa con el sub del atacante.
        String subjectExternoAntes = usuarioRepository.findByEmail("local-existente@test.com")
                .orElseThrow().getSubjectExterno();

        ResponseEntity<String> r = get("/api/v1/cuentas/proyectos",
                EmisorExternoDePrueba.token("sub-atacante-correo", Map.of("email", "local-existente@test.com")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(usuarioRepository.findByEmail("local-existente@test.com").orElseThrow().getSubjectExterno())
                .as("no se pisa con el sub del atacante").isEqualTo(subjectExternoAntes);
    }

    @Test
    @DisplayName("con email_verified=true el token vincula una cuenta local LEGACY (subject_externo null, "
            + "anterior a D28/Tesela) sin duplicarla y conserva sus proyectos")
    void vinculaConCorreoVerificado() throws Exception {
        // Desde D28/Tesela, registrar() YA vincula subject_externo al crear la cuenta (no queda
        // null) -- este escenario de "vincular una cuenta local SIN identidad externa todavia"
        // solo le puede pasar a una cuenta LEGACY (de antes de la migracion, ver
        // PLAN_MIGRACION_USUARIOS.md, no ejecutado a proposito). Se simula creando la fila
        // directamente, sin pasar por el endpoint de registro.
        Usuario local = nuevoUsuario("vinculable@test.com");
        proyectoService.crearProyecto(local.getId(), "Proyecto vinculable", null, "VNC");
        long antes = usuarioRepository.count();
        assertThat(local.getSubjectExterno()).as("cuenta legacy, sin identidad externa todavia").isNull();

        ResponseEntity<String> r = get("/api/v1/cuentas/proyectos", EmisorExternoDePrueba.token("sub-vinculable",
                Map.of("email", "vinculable@test.com", "email_verified", true)));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(r.getBody()).path("data")).hasSize(1);
        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(usuarioRepository.findByEmail("vinculable@test.com").orElseThrow().getSubjectExterno()).isEqualTo("sub-vinculable");
    }

    private Usuario nuevoUsuario(String email) {
        Plan free = planRepository.findByCodigo(Plan.CODIGO_FREE).orElseThrow();
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash(null);
        usuario.setNombre("Usuario legacy de prueba");
        usuario.setEstado(Usuario.ESTADO_ACTIVO);
        usuario.setPlan(free);
        usuario.setEmailVerificado(false);
        return usuarioRepository.save(usuario);
    }

    @Test
    @DisplayName("roles de sistema desde la BD: un usuario SOPORTE accede al endpoint admin con un token SIN claim roles; uno sin rol recibe 403")
    void rolesDeSistemaVienenDeLaBd() {
        TokenResponse owner = registrar("owner-roles-bd@test.com", "Owner");
        String codigo = crearProyecto(owner.accessToken(), "Proyecto roles BD", "RBD");

        // Usuario externo ya aprovisionado, al que se le asigna SOPORTE en la BD local
        String subSoporte = "sub-soporte";
        String tokenSoporte = EmisorExternoDePrueba.token(subSoporte, Map.of("email", "soporte-bd@test.com"));
        get("/api/v1/cuentas/proyectos", tokenSoporte); // aprovisiona
        Usuario soporte = usuarioRepository.findBySubjectExterno(subSoporte).orElseThrow();
        UsuarioRolSistema asignacion = new UsuarioRolSistema();
        asignacion.setUsuario(soporte);
        asignacion.setRolSistema(rolSistemaRepository.findByCodigo(RolSistema.ROL_SOPORTE).orElseThrow());
        usuarioRolSistemaRepository.save(asignacion);

        String ruta = "/api/v1/admin/proyectos/" + codigo + "/reconciliar-contador";
        ResponseEntity<String> conRol = restTemplate.exchange(url(ruta), HttpMethod.POST, new HttpEntity<>(auth(tokenSoporte)), String.class);
        assertThat(conRol.getStatusCode()).as("SOPORTE en la BD, token sin claim roles").isEqualTo(HttpStatus.OK);

        String tokenSinRol = EmisorExternoDePrueba.token("sub-sin-rol", Map.of("email", "sin-rol-bd@test.com"));
        ResponseEntity<String> sinRol = restTemplate.exchange(url(ruta), HttpMethod.POST, new HttpEntity<>(auth(tokenSinRol)), String.class);
        assertThat(sinRol.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el emisor local convive: sus tokens siguen funcionando y se resuelven por correo, no por sub")
    void emisorLocalConvive() {
        TokenResponse local = registrar("convive-local@test.com", "Convive Local");

        assertThat(get("/api/v1/cuentas/proyectos", local.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("un emisor desconocido es 401, y un token externo NO se acepta como si fuera local ni viceversa")
    void emisoresNoSeMezclan() {
        String deOtroEmisor = EmisorExternoDePrueba.token("http://otro-emisor.test", "x", Map.of("email", "x@test.com"));
        assertThat(get("/api/v1/cuentas/proyectos", deOtroEmisor).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Un token que dice ser del emisor LOCAL pero firmado con RS256 por otra clave: el decoder local
        // (HS256 con el secreto) lo rechaza, no cae al externo.
        String hacePasarPorLocal = EmisorExternoDePrueba.token("ms-xrello-cuentas", "1", Map.of("email", "convive-local@test.com"));
        assertThat(get("/api/v1/cuentas/proyectos", hacePasarPorLocal).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<String> get(String ruta, String token) {
        return restTemplate.exchange(url(ruta), HttpMethod.GET, new HttpEntity<>(auth(token)), String.class);
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
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
}
