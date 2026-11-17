package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.springboot.cloud.cagr.xrello.cuentas.exception.EmailYaRegistradoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.TokenReseteoClaveVencidoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.TokenVerificacionVencidoException;
import com.springboot.cloud.cagr.xrello.cuentas.security.TokenHasher;

/**
 * Adapter HTTP hacia el endpoint interno de ms-usuario ({@code /api/internal/usuarios/**}),
 * resuelto por nombre lógico vía Eureka y protegido con un circuit breaker Resilience4j
 * (instancia {@code usuarios}) — mismo patrón que {@code ms3-oauth.UsuarioRemotoClient}.
 *
 * <p>ms-xrello-cuentas deja de ser su propio directorio de identidades (D28/Tesela): el login y el
 * alta de cuenta se verifican/crean aquí, nunca contra una tabla local de contraseñas. Esto NO pasa
 * por {@code ms3-oauth} — es una llamada servidor-a-servidor directa a ms-usuario, igual que ya hace
 * ms3-oauth, por decisión explícita del dueño del ecosistema (app-xrello-web nunca habla con un
 * backend transversal, solo con su propio backend).</p>
 *
 * <p><b>Sin caché, a propósito</b> (igual que ms3-oauth): cachear un "sí" permitiría entrar con una
 * clave ya cambiada. <b>Sin fallback permisivo</b>: si ms-usuario no responde se lanza
 * {@link IllegalStateException}; la autenticación/el alta fallan cerrados.</p>
 */
@Component
@Profile("!test")
public class UsuarioDirectorioClient implements UsuarioDirectorioPort {

    private static final String CIRCUIT_BREAKER_NAME = "usuarios";
    private static final String AUTENTICAR_PATH = "/api/internal/usuarios/autenticar";
    private static final String CREAR_PATH = "/api/internal/usuarios";
    private static final String VERIFICAR_CORREO_PATH = "/api/internal/usuarios/verificar-correo";
    private static final String SOLICITAR_RESETEO_CLAVE_PATH = "/api/internal/usuarios/solicitar-reseteo-clave";
    private static final String CONFIRMAR_RESETEO_CLAVE_PATH = "/api/internal/usuarios/confirmar-reseteo-clave";
    private static final String HEADER_TOKEN = "X-Internal-Token";

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final String internalToken;

    public UsuarioDirectorioClient(RestClient.Builder restClientBuilder,
                                    CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                                    DirectorioUsuarioProperties props) {
        // Spring Boot NO falla ante un ${VAR} sin resolver al enlazar @ConfigurationProperties: deja el texto
        // literal "${VAR}" como valor. Por eso se comprueba aqui explicitamente (un secreto "${...}" es publico).
        String token = props.getInternalToken();
        if (!StringUtils.hasText(token) || token.contains("${")) {
            throw new IllegalStateException(
                    "app.directorio.usuario.internal-token (USUARIO_INTERNAL_TOKEN) no esta configurado: copia "
                            + ".env.example a .env y define un valor propio (igual al de ms-usuario)");
        }
        this.restClient = restClientBuilder.baseUrl(props.getBaseUrl()).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.internalToken = token;
    }

    @Override
    public Optional<UsuarioRemoto> autenticar(String usuario, String clave) {
        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME).run(
                () -> restClient.post()
                        .uri(AUTENTICAR_PATH)
                        .header(HEADER_TOKEN, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("usuario", usuario, "clave", clave))
                        .exchange((request, response) -> leer(response, 401)),
                throwable -> { throw propagarOTraducir(throwable, "No se pudo verificar las credenciales contra ms-usuario"); });
    }

    /**
     * Da de alta la identidad canónica en ms-usuario, SIN clave usable — {@code nuevaClave} la fija
     * recien en {@link #verificarCorreo} (ver Javadoc de {@code security.RegistroService}). La
     * cuenta necesita igual algun valor en el campo {@code clave} del lado de ms-usuario (su
     * contrato de alta no distingue "sin clave todavia"), asi que se manda un valor aleatorio de
     * un solo uso, generado aqui y descartado de inmediato — nadie lo conoce, ni siquiera quien se
     * registro, asi que nunca puede autenticar con el hasta que verifique el correo. {@code usuario}
     * y {@code correo} son el mismo valor (el correo que escribió la persona) — ver
     * {@code AuthService}/{@code RegistroService}.
     *
     * <p>El 409 (correo/usuario ya existe) se trata como resultado de negocio normal DENTRO del
     * circuit breaker (vacío, no una excepción) — mismo criterio que el 401 de {@link #autenticar}
     * — para que un intento de registro duplicado (algo que pasa en operación normal) nunca cuente
     * como fallo de infraestructura ni arriesgue abrir el circuito.</p>
     *
     * @throws EmailYaRegistradoException si el correo/usuario ya existe en ms-usuario (409)
     */
    @Override
    public UsuarioRemoto crear(String usuario, String correo, String nombres) {
        String claveTemporalDesechable = TokenHasher.generarTokenAleatorio();
        Optional<UsuarioRemoto> creado = circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME).run(
                () -> restClient.post()
                        .uri(CREAR_PATH)
                        .header(HEADER_TOKEN, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "usuario", usuario,
                                "clave", claveTemporalDesechable,
                                "correo", correo,
                                "nombres", nombres == null ? "" : nombres,
                                // Tesela: la cuenta nace sin verificar — ms-usuario genera el token
                                // y envia el correo de verificacion (ver AutenticacionServiceImpl.crear,
                                // ms-usuario). El CRUD admin de ms-usuario, usado para operadores de
                                // app_admin_web, sigue mandando activo=true por su cuenta.
                                "activo", false))
                        .exchange((request, response) -> leer(response, 409)),
                throwable -> { throw propagarOTraducir(throwable, "No se pudo crear la identidad en ms-usuario"); });
        return creado.orElseThrow(() -> new EmailYaRegistradoException(correo));
    }

    /**
     * 404/410 son resultados de negocio normales (un enlace viejo o ya usado) — se leen DENTRO
     * del circuit breaker para no contar como fallo de infraestructura (mismo criterio que el 401
     * de {@link #autenticar} y el 409 de {@link #crear}), y se lanzan como excepcion FUERA de
     * {@code run(...)}, una vez resuelto el resultado.
     */
    @Override
    public UsuarioRemoto verificarCorreo(String token, String nuevaClave) {
        ResultadoVerificacion resultado = circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME).run(
                () -> restClient.post()
                        .uri(VERIFICAR_CORREO_PATH)
                        .header(HEADER_TOKEN, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", token, "nuevaClave", nuevaClave))
                        .exchange((request, response) -> leerVerificacion(response)),
                throwable -> {
                    throw new com.springboot.cloud.cagr.xrello.cuentas.exception.DirectorioNoDisponibleException(
                            "No se pudo verificar el correo contra ms-usuario", throwable);
                });
        if (resultado.vencido()) {
            throw new TokenVerificacionVencidoException("El enlace de verificacion vencio");
        }
        if (resultado.usuario() == null) {
            throw new RecursoNoEncontradoException("Token de verificacion de correo invalido");
        }
        return resultado.usuario();
    }

    /**
     * SIEMPRE "tiene exito": ms-usuario responde 200 exista o no una cuenta con ese correo (ver
     * {@code AutenticacionServiceImpl.solicitarReseteoClave}) — no hay 404/409 que leer dentro del
     * circuit breaker aqui, a diferencia de {@link #autenticar}/{@link #crear}.
     */
    @Override
    public void solicitarReseteoClave(String correo) {
        circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME).run(
                () -> restClient.post()
                        .uri(SOLICITAR_RESETEO_CLAVE_PATH)
                        .header(HEADER_TOKEN, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("correo", correo))
                        .exchange((request, response) -> {
                            HttpStatusCode status = response.getStatusCode();
                            if (!status.is2xxSuccessful()) {
                                throw new IllegalStateException("ms-usuario respondio " + status);
                            }
                            return null;
                        }),
                throwable -> {
                    throw new com.springboot.cloud.cagr.xrello.cuentas.exception.DirectorioNoDisponibleException(
                            "No se pudo solicitar el reseteo de clave contra ms-usuario", throwable);
                });
    }

    /**
     * 404/410 son resultados de negocio normales (un enlace viejo o ya usado) — mismo criterio
     * que {@link #verificarCorreo}.
     */
    @Override
    public UsuarioRemoto confirmarReseteoClave(String token, String nuevaClave) {
        ResultadoVerificacion resultado = circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME).run(
                () -> restClient.post()
                        .uri(CONFIRMAR_RESETEO_CLAVE_PATH)
                        .header(HEADER_TOKEN, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", token, "nuevaClave", nuevaClave))
                        .exchange((request, response) -> leerVerificacion(response)),
                throwable -> {
                    throw new com.springboot.cloud.cagr.xrello.cuentas.exception.DirectorioNoDisponibleException(
                            "No se pudo confirmar el reseteo de clave contra ms-usuario", throwable);
                });
        if (resultado.vencido()) {
            throw new TokenReseteoClaveVencidoException("El enlace de reseteo de clave vencio");
        }
        if (resultado.usuario() == null) {
            throw new RecursoNoEncontradoException("Token de reseteo de clave invalido");
        }
        return resultado.usuario();
    }

    private record ResultadoVerificacion(UsuarioRemoto usuario, boolean vencido) {
    }

    /** Reutilizado por {@link #verificarCorreo} y {@link #confirmarReseteoClave}: misma forma de respuesta. */
    private ResultadoVerificacion leerVerificacion(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == 404) {
            return new ResultadoVerificacion(null, false);
        }
        if (status.value() == 410) {
            return new ResultadoVerificacion(null, true);
        }
        if (status.is2xxSuccessful()) {
            Envelope envelope = response.bodyTo(new ParameterizedTypeReference<Envelope>() { });
            if (envelope == null || envelope.data() == null) {
                throw new IllegalStateException("ms-usuario respondio 2xx sin datos");
            }
            return new ResultadoVerificacion(envelope.data(), false);
        }
        throw new IllegalStateException("ms-usuario respondio " + status);
    }

    /**
     * El codigo "de negocio" (401 en autenticar) es un resultado normal -> vacio, NO un fallo de
     * infraestructura: no cuenta contra el circuit breaker. Cualquier otro codigo no 2xx (403 por
     * token interno mal configurado, 5xx) si lo es.
     *
     * <p>BUG-E2E-002: un 400 (p.ej. {@code @Size} de ms-usuario rechazando un correo demasiado
     * largo) es un rechazo de ENTRADA, no una caida del servicio -- antes caia en el mismo
     * {@code IllegalStateException("ms-usuario respondio " + status)} generico que un 5xx real, y
     * {@link #propagarOTraducir} lo mostraba como "El servicio de identidad no responde" (503),
     * ocultando el motivo real y legible que ms-usuario SI devuelve en el cuerpo.</p>
     */
    private Optional<UsuarioRemoto> leer(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
                                          int codigoVacio) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == codigoVacio) {
            return Optional.empty();
        }
        if (status.is2xxSuccessful()) {
            Envelope envelope = response.bodyTo(new ParameterizedTypeReference<Envelope>() { });
            if (envelope == null || envelope.data() == null) {
                throw new IllegalStateException("ms-usuario respondio 2xx sin datos");
            }
            return Optional.of(envelope.data());
        }
        if (status.value() == 400) {
            Envelope envelope = response.bodyTo(new ParameterizedTypeReference<Envelope>() { });
            throw new IllegalArgumentException(
                    envelope != null && envelope.message() != null ? envelope.message() : "Datos invalidos");
        }
        // BUG-E2E-001: 403 de autenticar() = cuenta existente pero sin verificar (ver
        // AutenticacionServiceImpl, ms-usuario) -- resultado de negocio legible, no una caida del
        // servicio; sin esta rama caia en el IllegalStateException generico de abajo, envuelto como
        // "El servicio de identidad no responde" (503) en vez del aviso real.
        if (status.value() == 403) {
            Envelope envelope = response.bodyTo(new ParameterizedTypeReference<Envelope>() { });
            throw new com.springboot.cloud.cagr.xrello.cuentas.exception.CuentaNoVerificadaException(
                    envelope != null && envelope.message() != null ? envelope.message() : "Verifica tu correo para poder ingresar");
        }
        throw new IllegalStateException("ms-usuario respondio " + status);
    }

    /**
     * Fallback comun del circuit breaker: un {@link IllegalArgumentException} (ver {@link #leer}) es un 400
     * de ENTRADA que {@code CuentasExceptionHandler} ya traduce con su propio mensaje -- se repropaga tal
     * cual, SIN envolverlo en {@code DirectorioNoDisponibleException} (503 generico), para no ocultarlo
     * como si ms-usuario estuviera caido (BUG-E2E-002).
     *
     * <p>Devuelve la excepcion en vez de lanzarla (el llamador hace {@code throw propagarOTraducir(...)})
     * a proposito: {@code circuitBreakerFactory} es {@code CircuitBreakerFactory<?, ?>} (tipos borrados),
     * asi que un lambda-EXPRESION que invoque un metodo generico aqui infiere {@code Object} en vez de el
     * tipo real y no compila contra {@code Optional<UsuarioRemoto>}; un {@code throw <expresion>} dentro
     * de un lambda en BLOQUE, en cambio, nunca completa normalmente y es compatible con cualquier tipo de
     * retorno, igual que los {@code throw new ...} que reemplaza.</p>
     */
    private RuntimeException propagarOTraducir(Throwable throwable, String mensaje) {
        if (throwable instanceof IllegalArgumentException illegalArgumentException) {
            return illegalArgumentException;
        }
        // BUG-E2E-001: mismo criterio que IllegalArgumentException arriba -- es un resultado de
        // negocio legible (ver leer()), no una caida real de ms-usuario.
        if (throwable instanceof com.springboot.cloud.cagr.xrello.cuentas.exception.CuentaNoVerificadaException cuentaNoVerificada) {
            return cuentaNoVerificada;
        }
        return new com.springboot.cloud.cagr.xrello.cuentas.exception.DirectorioNoDisponibleException(mensaje, throwable);
    }

    /** Sobre {@code ApiResponse} de ms-commons; {@code message} solo se usa en el camino de error (400). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(UsuarioRemoto data, String message) {
    }
}
