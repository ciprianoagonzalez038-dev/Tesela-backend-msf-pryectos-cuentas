package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.commons.mensajeria.MensajeriaPort;
import com.springboot.cloud.cagr.commons.mensajeria.MensajeriaRemotaException;
import com.springboot.cloud.cagr.commons.mensajeria.dto.EnviarMensajeRemotoRequest;
import com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionCompletoPort;
import com.springboot.cloud.cagr.commons.parametrizacion.ParametrizacionRemotoException;
import com.springboot.cloud.cagr.commons.parametrizacion.dto.ParametroRemotoDto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.domain.InvitacionUso;
import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CupoProyectoLlenoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionAgotadaException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionEmailNoCoincideException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionEmailNoVerificadoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.InvitacionVencidaException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.LimiteExcedidoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.YaEsMiembroException;
import com.springboot.cloud.cagr.xrello.cuentas.limite.CodigosLimite;
import com.springboot.cloud.cagr.xrello.cuentas.limite.LimiteService;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionUsoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.ProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RolProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.security.TokenHasher;

/**
 * Invitaciones a un {@link Proyecto}. La autorizacion "solo OWNER o ADMIN"
 * para crear/listar/revocar la resuelve el controller con
 * {@code @PreAuthorize} (ver {@code security.AccesoProyecto}) — este servicio
 * NO la vuelve a verificar (XRELLO_CONTRATO.md: "la verificacion NO se repite
 * a mano dentro de los servicios").
 */
@Service
public class InvitacionService {

    private static final Logger log = LoggerFactory.getLogger(InvitacionService.class);

    /**
     * Mensaje de {@link InvitacionAgotadaException} para un enlace con todos sus usos consumidos. Es lo que ve el usuario:
     * {@code RegistroService} lo devuelve como {@code motivoInvitacionNoAplicada} y el frontend lo muestra tal cual en el aviso
     * «Tu cuenta se creó, pero la invitación no pudo aplicarse: …». La vista previa publica NO lo usa: ahi AGOTADA sigue dando
     * el 404 unificado (ver Javadoc de {@link #consultarInvitacion}), para no reabrir el canal de confirmacion de tokens.
     */
    public static final String MENSAJE_INVITACION_AGOTADA = "Esta invitación ya alcanzó su límite de usos.";

    /** Claves de parametrización (servicio {@code ms-xrello-cuentas}) para el correo de invitación. */
    private static final String PARAM_CORREO_INVITACION = "CORREO_PROYECTOS_INVITACION";
    private static final String PARAM_URL_INVITACION = "URL_INVITACION_PROYECTOS";
    private static final String PARAM_ASUNTO_INVITACION = "ASUNTO_PROYECTOS_INVITACION";

    private final InvitacionRepository invitacionRepository;
    private final InvitacionUsoRepository invitacionUsoRepository;
    private final ProyectoRepository proyectoRepository;
    private final RolProyectoRepository rolProyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LimiteService limiteService;
    private final ProyectoService proyectoService;
    private final EntityManager entityManager;
    private final MensajeriaPort mensajeriaPort;
    private final ParametrizacionCompletoPort parametrizacionCompletoPort;

    public InvitacionService(
            InvitacionRepository invitacionRepository,
            InvitacionUsoRepository invitacionUsoRepository,
            ProyectoRepository proyectoRepository,
            RolProyectoRepository rolProyectoRepository,
            UsuarioRepository usuarioRepository,
            LimiteService limiteService,
            ProyectoService proyectoService,
            EntityManager entityManager,
            MensajeriaPort mensajeriaPort,
            ParametrizacionCompletoPort parametrizacionCompletoPort) {
        this.invitacionRepository = invitacionRepository;
        this.invitacionUsoRepository = invitacionUsoRepository;
        this.proyectoRepository = proyectoRepository;
        this.rolProyectoRepository = rolProyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.limiteService = limiteService;
        this.proyectoService = proyectoService;
        this.entityManager = entityManager;
        this.mensajeriaPort = mensajeriaPort;
        this.parametrizacionCompletoPort = parametrizacionCompletoPort;
    }

    /**
     * {@code EMAIL_DIRIGIDO} fuerza {@code usosMaximos = 1} y exige {@code emailDestino}. Valida
     * que el proyecto TODAVIA tenga cupo libre antes de emitir el link (no lo reserva — el cupo
     * real se ocupa recien al aceptar, ver {@link #aceptarInvitacion}: una invitacion sin usar no
     * bloquea el cupo para otra via distinta).
     */
    @Transactional
    public InvitacionCreada crearInvitacion(
            String codigoProyecto, String tipo, String emailDestino, String rolCodigo,
            long diasVigencia, int usosMaximos, Long creadaPorId) {

        Proyecto proyecto = proyectoRepository.findByCodigo(codigoProyecto)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + codigoProyecto));

        int usosMaximosFinal = usosMaximos;
        if (Invitacion.TIPO_EMAIL_DIRIGIDO.equals(tipo)) {
            usosMaximosFinal = 1;
            if (emailDestino == null || emailDestino.isBlank()) {
                throw new IllegalArgumentException("emailDestino es obligatorio para invitaciones EMAIL_DIRIGIDO");
            }
        }

        verificarCupoDisponible(proyecto);

        RolProyecto rol = rolProyectoRepository.findByCodigo(rolCodigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol de proyecto " + rolCodigo));
        Usuario creadaPor = usuarioRepository.findById(creadaPorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + creadaPorId));

        String tokenEnClaro = TokenHasher.generarTokenAleatorio();

        Invitacion invitacion = new Invitacion();
        invitacion.setCodigo(UUID.randomUUID().toString());
        invitacion.setProyecto(proyecto);
        invitacion.setTokenHash(TokenHasher.sha256Hex(tokenEnClaro));
        invitacion.setTipo(tipo);
        invitacion.setEmailDestino(emailDestino);
        invitacion.setRolAsignado(rol);
        invitacion.setCreadaPor(creadaPor);
        invitacion.setFechaExpiracion(LocalDateTime.now().plusDays(diasVigencia));
        invitacion.setUsosMaximos(usosMaximosFinal);
        invitacion.setUsosActuales(0);
        invitacion.setEstado(Invitacion.ESTADO_ACTIVA);
        invitacionRepository.save(invitacion);

        if (Invitacion.TIPO_EMAIL_DIRIGIDO.equals(tipo)) {
            enviarCorreoInvitacion(invitacion, proyecto, rol, creadaPor, tokenEnClaro);
        }

        return new InvitacionCreada(invitacion, tokenEnClaro);
    }

    /**
     * Nunca propaga: un fallo aqui (ms-parametrizacion o ms-mensajeria caidos, plantilla faltante)
     * no debe tumbar la creacion de la invitacion — mismo criterio que
     * {@code AutenticacionServiceImpl.enviarCorreoVerificacion} en ms-usuario. Si falla, el enlace
     * ya quedo creado y el OWNER/ADMIN puede copiarlo a mano, como hoy.
     */
    private void enviarCorreoInvitacion(
            Invitacion invitacion, Proyecto proyecto, RolProyecto rol, Usuario creadaPor, String tokenEnClaro) {
        try {
            Map<String, ParametroRemotoDto> config = parametrizacionCompletoPort.cargarConfiguracionCompleta();
            String plantilla = valorParametro(config, PARAM_CORREO_INVITACION);
            String urlBase = valorParametro(config, PARAM_URL_INVITACION);
            String asuntoPlantilla = valorParametro(config, PARAM_ASUNTO_INVITACION);

            String enlace = urlBase + "?token=" + tokenEnClaro;
            String cuerpo = plantilla
                    .replace("{{NOMBRE_PROYECTO}}", escaparHtml(proyecto.getNombre()))
                    .replace("{{NOMBRE_INVITADOR}}", escaparHtml(creadaPor.getNombre()))
                    .replace("{{ROL}}", escaparHtml(rol.getNombre()))
                    .replace("{{ENLACE_INVITACION}}", enlace);
            String asunto = asuntoPlantilla
                    .replace("{{NOMBRE_PROYECTO}}", proyecto.getNombre())
                    .replace("{{NOMBRE_INVITADOR}}", creadaPor.getNombre());

            mensajeriaPort.enviar(EnviarMensajeRemotoRequest.builder()
                    .canal("EMAIL")
                    .destinatarios(List.of(invitacion.getEmailDestino()))
                    .asunto(asunto)
                    .contenido(cuerpo)
                    .html(true)
                    .servicioOrigen("ms-xrello-cuentas")
                    .build());
        } catch (ParametrizacionRemotoException | MensajeriaRemotaException ex) {
            log.error("No se pudo enviar el correo de invitacion para la invitacion codigo={}: {}",
                    invitacion.getCodigo(), ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Fallo inesperado generando/enviando el correo de invitacion para la invitacion codigo={}",
                    invitacion.getCodigo(), ex);
        }
    }

    private static String valorParametro(Map<String, ParametroRemotoDto> config, String nombre) {
        ParametroRemotoDto parametro = config.get(nombre);
        if (parametro == null || parametro.getValor() == null) {
            throw new IllegalStateException("Falta el parametro " + nombre + " (servicio ms-xrello-cuentas)");
        }
        return parametro.getValor();
    }

    private static String escaparHtml(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void verificarCupoDisponible(Proyecto proyecto) {
        Integer limite = limiteService.resolver(proyecto.getOwner().getId(), CodigosLimite.MIEMBROS_POR_PROYECTO);
        if (limite != null && proyecto.getMiembrosActuales() >= limite) {
            throw new CupoProyectoLlenoException(proyecto.getId());
        }
    }

    /**
     * Publico, sin autenticar — para la pantalla previa al registro. Nunca expone ids.
     *
     * <p><b>Mismo error, mismo codigo HTTP, exista o no el token</b> — bug encontrado en
     * auditoria (ver XRELLO_CONTRATO.md §18): antes de este cambio, un token inexistente daba 404
     * y un token existente pero vencido/revocado/agotado daba 410, dos respuestas distinguibles
     * que permitian confirmar si un token concreto alguna vez fue emitido. Como este endpoint es
     * publico y el token es la unica credencial, coleccionar esa distincion no tiene ningun uso
     * legitimo para el cliente — se unifica en un solo {@link RecursoNoEncontradoException} (404)
     * para ambos casos. Riesgo original ya era bajo (token de 256 bits, no fuerza-bruteable), pero
     * la corrección es gratis y cierra el canal por completo.</p>
     */
    @Transactional(readOnly = true)
    public InvitacionPreviewView consultarInvitacion(String tokenEnClaro) {
        Invitacion invitacion = invitacionRepository.findByTokenHash(TokenHasher.sha256Hex(tokenEnClaro))
                .filter(candidata -> !estaVencida(candidata))
                .orElseThrow(() -> new RecursoNoEncontradoException("Invitacion no encontrada"));

        return new InvitacionPreviewView(
                invitacion.getProyecto().getNombre(),
                invitacion.getProyecto().getOwner().getNombre(),
                invitacion.getRolAsignado().getNombre());
    }

    /** AGOTADA, o ACTIVA con todos los usos consumidos (filas anteriores a que existiera el estado AGOTADA). */
    private boolean estaAgotada(Invitacion invitacion) {
        return Invitacion.ESTADO_AGOTADA.equals(invitacion.getEstado())
                || (Invitacion.ESTADO_ACTIVA.equals(invitacion.getEstado())
                        && invitacion.getUsosActuales() >= invitacion.getUsosMaximos());
    }

    private boolean estaVencida(Invitacion invitacion) {
        return Invitacion.ESTADO_REVOCADA.equals(invitacion.getEstado())
                || Invitacion.ESTADO_AGOTADA.equals(invitacion.getEstado())
                || invitacion.getFechaExpiracion().isBefore(LocalDateTime.now())
                || invitacion.getUsosActuales() >= invitacion.getUsosMaximos();
    }

    /**
     * {@code REQUIRED} (no {@code REQUIRES_NEW}/{@code NESTED} — el primero deja la transaccion
     * ciega al usuario recien creado por {@code RegistroService#registrar}; el segundo falla con
     * {@code NestedTransactionNotSupportedException}, este {@code JpaDialect} no soporta
     * savepoints). Toda validacion de SOLO LECTURA corre ANTES de {@link #ocuparUso} — nada que
     * compensar si rechazan ahi, pero {@code noRollbackFor} igual debe cubrirlas: CUALQUIER
     * excepcion que escape de este metodo decide aqui si commitea o revierte la transaccion
     * COMPARTIDA, sin importar si hubo o no una escritura previa que deshacer (probado en
     * caliente: acotar esto solo a {@link CupoProyectoLlenoException} rompia el registro con
     * limite excedido, con {@code UnexpectedRollbackException}). Tras {@code ocuparUso}, la unica
     * causa de rechazo es esa misma {@code CupoProyectoLlenoException} (su UPDATE atomico no
     * puede adelantarse) — el catch deshace a mano el uso y, si {@code agregarMiembro} ya habia
     * ocupado el cupo, tambien el cupo. {@link YaEsMiembroException} NO esta en
     * {@code noRollbackFor}: viene de un {@code flush()} que falla de verdad, lo que ya deja la
     * transaccion no-commiteable a nivel del PROVEEDOR JPA — revierte todo por su cuenta.
     */
    @Transactional(noRollbackFor = {
            RecursoNoEncontradoException.class, InvitacionVencidaException.class,
            InvitacionEmailNoCoincideException.class, InvitacionEmailNoVerificadoException.class,
            LimiteExcedidoException.class,
            CupoProyectoLlenoException.class })
    public MiembroProyecto aceptarInvitacion(String tokenEnClaro, Long usuarioId, boolean usuarioCreadoEnEsteFlujo) {
        Invitacion invitacion = invitacionRepository.findByTokenHash(TokenHasher.sha256Hex(tokenEnClaro))
                .orElseThrow(() -> new RecursoNoEncontradoException("Invitacion no encontrada"));

        if (invitacion.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new InvitacionVencidaException("La invitacion esta vencida");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + usuarioId));

        if (Invitacion.TIPO_EMAIL_DIRIGIDO.equals(invitacion.getTipo())
                && !usuario.getEmail().equalsIgnoreCase(invitacion.getEmailDestino())) {
            throw new InvitacionEmailNoCoincideException(
                    "El correo del usuario no coincide con el destinatario de la invitacion");
        }

        // Mitigacion de correo NO verificado (XRELLO_CONTRATO.md, deuda abierta): una dirigida solo
        // la acepta un usuario que YA existia, nunca la cuenta creada en este mismo flujo.
        if (Invitacion.TIPO_EMAIL_DIRIGIDO.equals(invitacion.getTipo()) && usuarioCreadoEnEsteFlujo) {
            throw new InvitacionEmailNoVerificadoException(
                    "Esta invitacion va dirigida a un correo: la cuenta recien creada no puede usarla en el mismo "
                            + "paso porque el correo aun no esta verificado. Inicia sesion de nuevo y acepta la invitacion");
        }

        // Agotada ANTES de tocar nada: mensaje claro para quien llega con un enlace ya consumido (en el registro, este
        // mensaje es el "motivo" que ve el usuario). Cubre tambien filas heredadas que llegaron a usos_maximos
        // sin pasar a AGOTADA. Una revocada/carrera cae mas abajo en el mensaje generico de siempre (ver ocuparUso).
        if (estaAgotada(invitacion)) {
            throw new InvitacionAgotadaException(MENSAJE_INVITACION_AGOTADA);
        }

        // Capturar ESTOS valores ANTES de ocuparUso: ese @Modifying tiene clearAutomatically=true
        // (limpia el contexto de persistencia), y despues de eso "invitacion" queda detached --
        // sus asociaciones LAZY (proyecto/rolAsignado/creadaPor), si nunca se inicializaron,
        // explotarian con LazyInitializationException ("no session") al navegarlas. Verificado
        // en caliente: exactamente ese fallo, intermitente bajo concurrencia real.
        Long invitacionId = invitacion.getId();
        Long proyectoId = invitacion.getProyecto().getId();
        String rolCodigo = invitacion.getRolAsignado().getCodigo();
        Long creadaPorId = invitacion.getCreadaPor().getId();

        // Limite PROYECTOS_COMO_INVITADO del usuario que se une -- ANTES de ocuparUso, a
        // proposito (ver Javadoc del metodo): ninguna escritura ha ocurrido todavia, asi que no
        // hay nada que compensar si rechaza.
        limiteService.verificarPuedeAceptarInvitacion(usuarioId);

        // Ocupa el USO con UPDATE atomico (estado='ACTIVA' AND usos_actuales<usos_maximos) --
        // 0 filas cubre agotada Y revocada por igual, sin distinguir (ver Javadoc del repositorio).
        // Si 0, no hay nada que compensar -- el UPDATE no cambio ninguna fila.
        int filas = invitacionRepository.ocuparUso(invitacionId, Invitacion.ESTADO_ACTIVA, Invitacion.ESTADO_AGOTADA);
        if (filas == 0) {
            throw new InvitacionAgotadaException("La invitacion ya no tiene usos disponibles");
        }

        // A partir de aqui, cualquier fallo debe liberar el uso recien ocupado -- y, si
        // agregarMiembro ya alcanzo a ocupar el cupo, tambien el cupo (ver Javadoc del metodo:
        // sin rollback automatico que lo haga por nosotros para CupoProyectoLlenoException).
        MiembroProyecto miembro = null;
        try {
            // agregarMiembro hace su propio UPDATE atomico contra el plan del OWNER (no el de
            // este usuario). Puede lanzar CupoProyectoLlenoException (sobrevive, se compensa
            // abajo) o YaEsMiembroException (no sobrevive -- ver su Javadoc).
            miembro = proyectoService.agregarMiembro(proyectoId, usuarioId, rolCodigo, creadaPorId);

            // Referencia fresca (post clearAutomatically de arriba), nunca "invitacion" -- mismo
            // motivo que proyectoId/rolCodigo/creadaPorId de arriba.
            InvitacionUso uso = new InvitacionUso();
            uso.setInvitacion(invitacionRepository.getReferenceById(invitacionId));
            uso.setUsuario(usuarioRepository.getReferenceById(usuarioId));
            uso.setFecha(LocalDateTime.now());
            try {
                invitacionUsoRepository.saveAndFlush(uso);
            } catch (DataIntegrityViolationException ex) {
                // "uso" queda en el contexto de persistencia con id null tras el flush fallido --
                // el catch exterior necesita llamar invitacionRepository.liberarUso justo
                // despues, y eso auto-flushea primero (mismo problema, misma solucion que
                // ProyectoService#agregarMiembro).
                entityManager.clear();
                throw new YaEsMiembroException(proyectoId, usuarioId);
            }

            log.info("Invitacion aceptada: proyectoId={} usuarioId={}", proyectoId, usuarioId);
            return miembro;
        } catch (RuntimeException ex) {
            invitacionRepository.liberarUso(invitacionId, Invitacion.ESTADO_AGOTADA, Invitacion.ESTADO_ACTIVA);
            // agregarMiembro YA habia ocupado el cupo cuando el fallo ocurrio despues (hoy, solo
            // CupoProyectoLlenoException llega hasta aqui con "miembro" no-nulo -- ver Tarea 0 del
            // contrato: sin esto, un rechazo posterior a agregarMiembro que algun dia no venga de
            // un flush fallido dejaria miembros_actuales incrementado sin compensar).
            if (miembro != null) {
                proyectoRepository.decrementarMiembros(proyectoId);
            }
            throw ex;
        }
    }

    /**
     * Recibe el {@code codigo} UUID de la invitacion, nunca su id numerico interno — mismo
     * criterio que {@link Proyecto} (ver XRELLO_CONTRATO.md §5).
     *
     * <p><b>{@code codigoProyecto} es OBLIGATORIO, no decorativo</b> — bug real encontrado y
     * corregido (ver XRELLO_CONTRATO.md, regla "Identificador protegido vs identificador
     * mutado"): el {@code @PreAuthorize} del controller verifica que el caller sea OWNER/ADMIN
     * de {@code codigoProyecto} (el proyecto DE LA URL), pero antes de este cambio el método
     * buscaba la invitación SOLO por su propio {@code codigoInvitacion}, de forma global — un
     * OWNER/ADMIN de CUALQUIER proyecto podía revocar la invitación de OTRO proyecto cuyo UUID
     * conociera. {@link InvitacionRepository#findByCodigoAndProyecto_Codigo} exige la PAREJA:
     * si {@code codigoInvitacion} no pertenece a {@code codigoProyecto}, es 404 — nunca se toca
     * una invitación de otro proyecto, sin importar lo que el caller sepa o adivine.</p>
     */
    @Transactional
    public void revocarInvitacion(String codigoProyecto, String codigoInvitacion) {
        Invitacion invitacion = invitacionRepository.findByCodigoAndProyecto_Codigo(codigoInvitacion, codigoProyecto)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la invitacion " + codigoInvitacion + " en el proyecto " + codigoProyecto));
        invitacion.setEstado(Invitacion.ESTADO_REVOCADA);
    }

    /** Nunca expone {@code tokenHash} — ver el mapeo del controller a {@code web.dto}. */
    @Transactional(readOnly = true)
    public List<Invitacion> listarInvitaciones(String codigoProyecto) {
        Proyecto proyecto = proyectoRepository.findByCodigo(codigoProyecto)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + codigoProyecto));
        return invitacionRepository.findByProyecto_Id(proyecto.getId());
    }
}
