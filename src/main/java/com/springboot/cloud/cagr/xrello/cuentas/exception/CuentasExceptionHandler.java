package com.springboot.cloud.cagr.xrello.cuentas.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.springboot.cloud.cagr.commons.dto.ApiResponse;

/**
 * Traduce las excepciones propias de ms-xrello-cuentas a {@link ApiResponse}
 * (ver XRELLO_CONTRATO.md §5 — toda respuesta, exito y error, se envuelve en
 * {@code ApiResponse<T>}).
 *
 * <p>Sin {@code assignableTypes}: a diferencia de {@code ms-onboarding} (que
 * separa controllers de ejecucion y administrativos con advices propios que
 * compiten entre si), este proyecto todavia no tiene ningun controller — este
 * es el UNICO advice propio del servicio, pensado para aplicar a todos los
 * controllers que se agreguen. {@code @Order(HIGHEST_PRECEDENCE)} sigue
 * siendo necesario para ganarle al catch-all {@code Exception.class} de
 * {@code GlobalExceptionHandler} (ms-commons, {@code @Order(LOWEST_PRECEDENCE)})
 * — sin esto, una excepcion propia arriesga caer en el 500 generico en vez
 * del codigo HTTP especifico que este advice define.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CuentasExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CuentasExceptionHandler.class);

    @ExceptionHandler(CupoProyectoLlenoException.class)
    public ResponseEntity<ApiResponse<Void>> handleCupoProyectoLleno(CupoProyectoLlenoException ex) {
        log.info("Cupo de proyecto lleno: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(YaEsMiembroException.class)
    public ResponseEntity<ApiResponse<Void>> handleYaEsMiembro(YaEsMiembroException ex) {
        log.info("Usuario ya es miembro: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(NoSePuedeExpulsarOwnerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSePuedeExpulsarOwner(NoSePuedeExpulsarOwnerException ex) {
        log.info("Intento de expulsar al owner: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<ApiResponse<Void>> handleLimiteExcedido(LimiteExcedidoException ex) {
        log.info("Limite excedido: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()));
    }

    /**
     * Captura tambien {@link LimiteNoConfiguradoException} (subclase) — mismo
     * codigo 404, un limite sin configurar es, en esencia, un recurso de
     * configuracion que no existe.
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiResponse<Void>> handleRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /** ms-usuario lento o caído: el login/alta fallan cerrados pero con 503 (reintentable), no con 500. */
    @ExceptionHandler(DirectorioNoDisponibleException.class)
    public ResponseEntity<ApiResponse<Void>> handleDirectorioNoDisponible(DirectorioNoDisponibleException ex) {
        log.error("Directorio de usuarios no disponible: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de identidad no responde en este momento. Inténtalo de nuevo en unos segundos."));
    }

    /** Captura tambien {@link com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionEmailNoCoincideException}. */
    @ExceptionHandler(SinPermisoException.class)
    public ResponseEntity<ApiResponse<Void>> handleSinPermiso(SinPermisoException ex) {
        log.warn("Operacion no permitida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    /**
     * Cubre credenciales de login invalidas Y un refresh token inexistente/expirado —
     * mismo mensaje generico, mismo 401 (ver Javadoc de clase).
     */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiResponse<Void>> handleCredencialesInvalidas(CredencialesInvalidasException ex) {
        log.warn("Credenciales invalidas: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(ReusoRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleReusoRefreshToken(ReusoRefreshTokenException ex) {
        log.warn("Reuso de refresh token detectado, sesiones revocadas: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    /** BUG-E2E-021: mismo owner, mismo prefijo -> 409 en vez del 500 generico que daba la constraint sin traducir. */
    @ExceptionHandler(PrefijoYaUsadoException.class)
    public ResponseEntity<ApiResponse<Void>> handlePrefijoYaUsado(PrefijoYaUsadoException ex) {
        log.info("Prefijo de proyecto ya usado por el propietario: {}", ex.getPrefijo());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /** BUG-E2E-001: cuenta sin verificar al intentar iniciar sesion -- 403 con el mensaje de ms-usuario, no un 503. */
    @ExceptionHandler(CuentaNoVerificadaException.class)
    public ResponseEntity<ApiResponse<Void>> handleCuentaNoVerificada(CuentaNoVerificadaException ex) {
        log.info("Intento de login con cuenta sin verificar");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(EmailYaRegistradoException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailYaRegistrado(EmailYaRegistradoException ex) {
        // Sin ex.getMessage() aqui a proposito (ver XRELLO_CONTRATO.md §18): ese mensaje incluye
        // el email completo (es util devolverlo al propio caller, que ya lo escribio el mismo),
        // pero NO debe quedar persistido en el log de la aplicacion a cada intento de registro.
        log.info("Intento de registro con un email ya existente");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /** Captura tambien {@link InvitacionAgotadaException} (subclase) — mismo 410. */
    @ExceptionHandler(InvitacionVencidaException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvitacionVencida(InvitacionVencidaException ex) {
        log.info("Invitacion vencida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error(HttpStatus.GONE, ex.getMessage()));
    }

    /** Token de verificacion de correo vencido o ya usado (ver {@code VerificacionCorreoPublicoController}). */
    @ExceptionHandler(TokenVerificacionVencidoException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenVerificacionVencido(TokenVerificacionVencidoException ex) {
        log.info("Token de verificacion de correo vencido/usado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error(HttpStatus.GONE, ex.getMessage()));
    }

    /** Token de reseteo de clave vencido o ya usado (ver {@code AuthController}). */
    @ExceptionHandler(TokenReseteoClaveVencidoException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenReseteoClaveVencido(TokenReseteoClaveVencidoException ex) {
        log.info("Token de reseteo de clave vencido/usado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error(HttpStatus.GONE, ex.getMessage()));
    }

    /**
     * {@code @PreAuthorize} denegado (ver {@code security.AccesoProyecto}) lanza
     * {@code org.springframework.security.authorization.AuthorizationDeniedException} —
     * subclase de {@link AccessDeniedException} desde Spring Security 6.3. Sin este handler,
     * la excepcion (lanzada DESDE DENTRO de la invocacion del controller, no desde el filtro de
     * seguridad) es interceptada por el catch-all {@code Exception.class} de
     * {@code GlobalExceptionHandler} (ms-commons) ANTES de llegar a
     * {@code ExceptionTranslationFilter} — {@code DispatcherServlet} resuelve la excepcion via
     * {@code HandlerExceptionResolver} (que incluye cualquier {@code @RestControllerAdvice}) en
     * el mismo {@code doDispatch}, sin propagarla de vuelta al filtro. Verificado en caliente:
     * sin este handler, un {@code @PreAuthorize} denegado devolvia 500, no 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado por @PreAuthorize: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN, "No tiene permisos para esta operacion"));
    }

    /**
     * Entrada invalida que no justifica una excepcion de dominio propia (ej. {@code ProyectoService#normalizarPrefijo}
     * rechazando un prefijo que no empieza con una letra). Sin este handler caia en el catch-all 500 generico de
     * {@code GlobalExceptionHandler} (ms-commons) — reportado 2026-11-14: crear un proyecto con un prefijo autosugerido
     * que empezaba con un digito (ej. "1AB") respondia error 500 en vez de un mensaje claro.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.info("Solicitud invalida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }
}
