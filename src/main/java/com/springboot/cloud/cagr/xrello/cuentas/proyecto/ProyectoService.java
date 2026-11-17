package com.springboot.cloud.cagr.xrello.cuentas.proyecto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.xrello.cuentas.domain.MiembroProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CupoProyectoLlenoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.NoSePuedeExpulsarOwnerException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.SinPermisoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.YaEsMiembroException;
import com.springboot.cloud.cagr.xrello.cuentas.limite.CodigosLimite;
import com.springboot.cloud.cagr.xrello.cuentas.limite.LimiteService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.MiembroProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.ProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RolProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

/**
 * Orquesta proyectos, membresias y el control de cupo atomico (ver
 * XRELLO_CONTRATO.md §6 y {@code limite.LimiteService}).
 */
@Service
public class ProyectoService {

    private static final Logger log = LoggerFactory.getLogger(ProyectoService.class);

    /** Una letra en mayuscula seguida de 1 a 9 letras/digitos en mayuscula (2 a 10 caracteres). */
    private static final Pattern PATRON_PREFIJO = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    private final ProyectoRepository proyectoRepository;
    private final MiembroProyectoRepository miembroProyectoRepository;
    private final RolProyectoRepository rolProyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LimiteService limiteService;
    private final EntityManager entityManager;

    public ProyectoService(
            ProyectoRepository proyectoRepository,
            MiembroProyectoRepository miembroProyectoRepository,
            RolProyectoRepository rolProyectoRepository,
            UsuarioRepository usuarioRepository,
            LimiteService limiteService,
            EntityManager entityManager) {
        this.proyectoRepository = proyectoRepository;
        this.miembroProyectoRepository = miembroProyectoRepository;
        this.rolProyectoRepository = rolProyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.limiteService = limiteService;
        this.entityManager = entityManager;
    }

    /**
     * Conveniencia histórica: crea el proyecto sin elegir plantilla (equivale a
     * {@code plantillaCodigo = null} — ms-xrello-tableros usa la predeterminada, ver
     * {@code InicializacionProyectoService#inicializarSiHaceFalta(Long)} allá). Mismo patrón que
     * esa clase: la sobrecarga corta delega en la completa, así ningún llamador existente se
     * rompe por el nuevo parámetro.
     */
    @Transactional
    public Proyecto crearProyecto(Long usuarioId, String nombre, String descripcion, String prefijo) {
        return crearProyecto(usuarioId, nombre, descripcion, prefijo, null);
    }

    /**
     * Crea un proyecto nuevo, ACTIVO, con {@code miembros_actuales = 1}, y en la
     * MISMA transaccion la fila {@code cta_miembro_proyecto} del owner (rol
     * OWNER). Verifica el limite ANTES de crear nada.
     *
     * <p>{@code plantillaCodigo}: elegido por el usuario entre el catálogo de plantillas de
     * ms-xrello-tableros ({@code null}/vacío = la predeterminada). Se guarda tal cual, SIN
     * validar contra ese catálogo — vive en otro microservicio, sin FK física (ver
     * XRELLO_CONTRATO.md §19); si el código no existe, {@code InicializacionProyectoService}
     * allá lo rechaza en el primer acceso perezoso, no aquí.</p>
     */
    @Transactional
    public Proyecto crearProyecto(Long usuarioId, String nombre, String descripcion, String prefijo, String plantillaCodigo) {
        limiteService.verificarPuedeCrearProyecto(usuarioId);

        String prefijoNormalizado = normalizarPrefijo(prefijo);

        Usuario owner = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + usuarioId));
        RolProyecto rolOwner = rolProyectoRepository.findByCodigo(RolProyecto.ROL_OWNER)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + RolProyecto.ROL_OWNER));

        Proyecto proyecto = new Proyecto();
        proyecto.setCodigo(UUID.randomUUID().toString());
        proyecto.setNombre(nombre);
        proyecto.setDescripcion(descripcion);
        proyecto.setPrefijo(prefijoNormalizado);
        proyecto.setOwner(owner);
        proyecto.setMiembrosActuales(1);
        proyecto.setEstado(Proyecto.ESTADO_ACTIVO);
        proyecto.setPlantillaCodigo(plantillaCodigo == null || plantillaCodigo.isBlank() ? null : plantillaCodigo);
        try {
            proyecto = proyectoRepository.save(proyecto);
        } catch (DataIntegrityViolationException ex) {
            // BUG-E2E-021: mismo propietario, mismo prefijo -> viola uq_cta_proyecto_owner_prefijo. Sin este
            // catch caia en el 500 generico de GlobalExceptionHandler en vez de un 409 claro. @GeneratedValue
            // IDENTITY (ver Proyecto.java): el INSERT ocurre en el propio save(), no hace falta saveAndFlush.
            // clear() por el mismo motivo que agregarMiembro (abajo): "proyecto" queda con id null tras el
            // fallo y cualquier operacion mas en esta sesion revienta si no se limpia primero.
            entityManager.clear();
            throw new com.springboot.cloud.cagr.xrello.cuentas.exception.PrefijoYaUsadoException(prefijoNormalizado);
        }

        MiembroProyecto miembro = new MiembroProyecto();
        miembro.setProyecto(proyecto);
        miembro.setUsuario(owner);
        miembro.setRolProyecto(rolOwner);
        miembro.setInvitadoPor(null);
        miembro.setFechaIngreso(LocalDateTime.now());
        miembroProyectoRepository.save(miembro);

        log.info("Proyecto creado: proyectoId={} usuarioId={}", proyecto.getId(), usuarioId);
        return proyecto;
    }

    private String normalizarPrefijo(String prefijo) {
        if (prefijo == null) {
            throw new IllegalArgumentException("El prefijo es obligatorio");
        }
        String normalizado = prefijo.toUpperCase(Locale.ROOT);
        if (!PATRON_PREFIJO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException(
                    "El prefijo '" + prefijo + "' no cumple el formato requerido "
                            + "(una letra seguida de 1 a 9 letras/digitos, en mayusculas)");
        }
        return normalizado;
    }

    /**
     * Proyectos ACTIVOS del usuario, con su rol y si es owner, en UNA sola
     * consulta (ver {@code MiembroProyectoRepository#findProyectosConRolByUsuarioId}).
     */
    @Transactional(readOnly = true)
    public List<ProyectoConRolView> listarProyectosDeUsuario(Long usuarioId) {
        return miembroProyectoRepository.findProyectosConRolByUsuarioId(
                usuarioId, Proyecto.ESTADO_ACTIVO, RolProyecto.ROL_OWNER);
    }

    /**
     * Punto de entrada para los controllers: resuelve el {@code codigo} UUID que reciben del
     * cliente (nunca el id numerico interno, ver XRELLO_CONTRATO.md §5).
     */
    @Transactional(readOnly = true)
    public Proyecto obtenerPorCodigo(String codigo) {
        return proyectoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + codigo));
    }

    /** Miembros del proyecto con {@code usuario}/{@code rolProyecto} pre-cargados (sin N+1). */
    @Transactional(readOnly = true)
    public List<MiembroProyecto> listarMiembros(String codigoProyecto) {
        return miembroProyectoRepository.findByProyecto_CodigoFetchUsuarioYRol(codigoProyecto);
    }

    /**
     * Archiva el proyecto — deja de contar para cualquier limite de cupo (ver
     * {@code LimiteService}). Actualizacion por dirty-checking (con
     * {@code @Version}), no requiere sentencia nativa: no es una operacion de
     * alta concurrencia como {@link #agregarMiembro}.
     */
    @Transactional
    public void archivarProyecto(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + proyectoId));
        proyecto.setEstado(Proyecto.ESTADO_ARCHIVADO);
    }

    /** Variante por {@code codigo} para el controller (ver {@link #archivarProyecto(Long)}). */
    @Transactional
    public void archivarProyectoPorCodigo(String codigo) {
        archivarProyecto(obtenerPorCodigo(codigo).getId());
    }

    /**
     * Transfiere la propiedad del proyecto a {@code nuevoOwnerId}. Exige que el
     * destinatario YA sea miembro del proyecto y que tenga cupo de
     * {@code PROYECTOS_COMO_OWNER} disponible. El owner actual NUNCA pierde su
     * membresia ni es expulsado — solo cambia de rol (a ADMIN); la fila de
     * {@code cta_miembro_proyecto} se conserva.
     */
    @Transactional
    public void transferirPropiedad(Long proyectoId, Long nuevoOwnerId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + proyectoId));

        Long ownerActualId = proyecto.getOwner().getId();
        if (ownerActualId.equals(nuevoOwnerId)) {
            log.info("transferirPropiedad no-op: el usuario {} ya es el owner del proyecto {}", nuevoOwnerId, proyectoId);
            return;
        }

        MiembroProyecto nuevoOwnerMembresia = miembroProyectoRepository
                .findByProyecto_IdAndUsuario_Id(proyectoId, nuevoOwnerId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + nuevoOwnerId + " no es miembro del proyecto " + proyectoId
                                + "; no se le puede transferir la propiedad"));

        // Cupo de PROYECTOS_COMO_OWNER del destinatario — unico limite exigido para transferir
        // (a diferencia de crearProyecto, no se revisa PROYECTOS_TOTAL: el destinatario ya
        // cuenta como miembro de este proyecto, su total no cambia con la transferencia).
        limiteService.verificarPuedeSerOwnerDeUnProyectoMas(nuevoOwnerId);

        MiembroProyecto ownerActualMembresia = miembroProyectoRepository
                .findByProyecto_IdAndUsuario_Id(proyectoId, ownerActualId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Membresia del owner actual no encontrada para el proyecto " + proyectoId
                                + " (dato inconsistente)"));

        RolProyecto rolAdmin = rolProyectoRepository.findByCodigo(RolProyecto.ROL_ADMIN)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + RolProyecto.ROL_ADMIN));
        RolProyecto rolOwner = rolProyectoRepository.findByCodigo(RolProyecto.ROL_OWNER)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + RolProyecto.ROL_OWNER));

        // No se borra ni se "expulsa" la membresia del owner saliente — solo se degrada su rol.
        ownerActualMembresia.setRolProyecto(rolAdmin);
        nuevoOwnerMembresia.setRolProyecto(rolOwner);
        proyecto.setOwner(nuevoOwnerMembresia.getUsuario());
    }

    /**
     * Variante por {@code codigo}/email para el controller: el nuevo owner se identifica por
     * email (no tiene un {@code codigo} publico propio como proyecto/invitacion), nunca por su
     * id numerico interno (ver {@link #transferirPropiedad(Long, Long)}).
     *
     * <p><b>Un solo mensaje, exista o no el email</b> — bug encontrado en auditoria (ver
     * XRELLO_CONTRATO.md §18): antes de este cambio, "el email no existe" y "el email existe pero
     * no es miembro de este proyecto" daban dos 404 con MENSAJES distintos — un OWNER autenticado
     * podia usar esta operacion para enumerar que correos tienen cuenta en la plataforma, probando
     * emails arbitrarios contra su propio proyecto. Ahora ambos casos dan el mismo mensaje
     * generico, sin distinguir "no existe" de "existe pero no es miembro" — y SIN repetir el email
     * recibido, ni en la respuesta ni en el log: {@code CuentasExceptionHandler} loguea
     * {@code ex.getMessage()} de cualquier {@link RecursoNoEncontradoException} a nivel WARN, así
     * que incluir el email aquí lo dejaría en el log de la aplicación sin necesidad real.</p>
     */
    @Transactional
    public void transferirPropiedadPorCodigo(String codigo, String emailNuevoOwner) {
        Proyecto proyecto = obtenerPorCodigo(codigo);
        Usuario nuevoOwner = usuarioRepository.findByEmail(emailNuevoOwner).orElse(null);
        boolean esMiembro = nuevoOwner != null
                && miembroProyectoRepository.findByProyecto_IdAndUsuario_Id(proyecto.getId(), nuevoOwner.getId()).isPresent();
        if (!esMiembro) {
            throw new RecursoNoEncontradoException("El usuario indicado no es miembro de este proyecto");
        }
        transferirPropiedad(proyecto.getId(), nuevoOwner.getId());
    }

    /**
     * Agrega un miembro ocupando el cupo de {@code MIEMBROS_POR_PROYECTO} con UNA sentencia
     * nativa atomica (PROHIBIDO {@code SELECT COUNT(*)} seguido de {@code INSERT}, ver
     * XRELLO_CONTRATO.md) — el limite se resuelve contra el plan del OWNER, nunca el del usuario
     * que se une. 0 filas afectadas = cupo lleno → {@link CupoProyectoLlenoException}, sin que
     * nada se haya escrito todavia.
     *
     * <p>Si el INSERT viola {@code uq_cta_miembro_proyecto_proyecto_usuario} (ya era miembro —
     * alcanzable tanto desde el registro con invitacion como desde
     * {@code AceptarInvitacionController}, un usuario YA EXISTENTE), se traduce a
     * {@link YaEsMiembroException} — NUNCA en {@code noRollbackFor}: viene de un {@code flush()}
     * que REALMENTE falla, lo que deja la transaccion no-commiteable a nivel del proveedor JPA
     * sin importar Spring — revierte TODO por su cuenta (el incremento incluido), llamado solo
     * (su propia transaccion) o anidado en {@code InvitacionService#aceptarInvitacion}.</p>
     *
     * <p>{@code noRollbackFor = CupoProyectoLlenoException}: esa SI es segura — nunca involucra
     * un flush fallido. Sin esto, {@code aceptarInvitacion} perderia el alta del usuario cuando
     * corre dentro del registro (ver su Javadoc).</p>
     */
    @Transactional(noRollbackFor = CupoProyectoLlenoException.class)
    public MiembroProyecto agregarMiembro(Long proyectoId, Long usuarioId, String rolCodigo, Long invitadoPorId) {
        if (RolProyecto.ROL_OWNER.equals(rolCodigo)) {
            throw new SinPermisoException(
                    "No se puede agregar un miembro con rol OWNER directamente; usar transferirPropiedad.");
        }

        Long ownerId = proyectoRepository.findOwnerId(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + proyectoId));

        // REGLA CRITICA: el limite de miembros se resuelve contra el PLAN DEL OWNER,
        // nunca contra el del usuario que se esta uniendo (usuarioId).
        Integer limiteMiembros = limiteService.resolver(ownerId, CodigosLimite.MIEMBROS_POR_PROYECTO);

        int filasActualizadas = (limiteMiembros == null)
                ? proyectoRepository.incrementarMiembrosSinLimite(proyectoId)
                : proyectoRepository.incrementarMiembrosConLimite(proyectoId, limiteMiembros);

        if (filasActualizadas == 0) {
            throw new CupoProyectoLlenoException(proyectoId);
        }

        RolProyecto rol = rolProyectoRepository.findByCodigo(rolCodigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol de proyecto " + rolCodigo));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + usuarioId));
        Usuario invitadoPor = null;
        if (invitadoPorId != null) {
            invitadoPor = usuarioRepository.findById(invitadoPorId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + invitadoPorId));
        }

        // findById (entidad completa), NO getReferenceById: este metodo se llama desde
        // invitacion.InvitacionService#aceptarInvitacion, y para cuando el controller (o
        // RegistroService, si vino de un registro con invitacion) lee el MiembroProyecto
        // devuelto, la transaccion que lo creo ya cerro -- un proxy LAZY sin inicializar
        // explotaria con LazyInitializationException en ese momento. Una entidad completa
        // sigue siendo segura de navegar (sus campos escalares) despues de detach.
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el proyecto " + proyectoId));

        MiembroProyecto miembro = new MiembroProyecto();
        miembro.setProyecto(proyecto);
        miembro.setUsuario(usuario);
        miembro.setRolProyecto(rol);
        miembro.setInvitadoPor(invitadoPor);
        miembro.setFechaIngreso(LocalDateTime.now());

        try {
            miembroProyectoRepository.saveAndFlush(miembro);
        } catch (DataIntegrityViolationException ex) {
            // "miembro" queda en el contexto de persistencia con id null tras el flush fallido --
            // Hibernate revienta ("has a null identifier") si se intenta CUALQUIER operacion mas
            // en esta sesion sin limpiarlo primero (afecta incluso al caller: aceptarInvitacion
            // necesita llamar invitacionRepository.liberarUso justo despues de que esto se
            // propague). clear() completo, no detach() puntual: el id es null, Hibernate no puede
            // construir el EntityKey para desprenderlo por separado.
            entityManager.clear();
            throw new YaEsMiembroException(proyectoId, usuarioId);
        }

        return miembro;
    }

    /**
     * Quita a un miembro del proyecto y libera su cupo (decremento atomico de
     * {@code miembros_actuales} en la MISMA transaccion, ver
     * {@code ProyectoRepository#decrementarMiembros}).
     *
     * <p>Dos invariantes de dominio, ambas verificadas ANTES de borrar nada:</p>
     * <ul>
     *   <li>Al OWNER nunca se le puede quitar la membresia por esta via (ver Javadoc de
     *       {@link #transferirPropiedad} — el owner solo deja de serlo transfiriendo la
     *       propiedad, nunca siendo removido) — {@link NoSePuedeExpulsarOwnerException} (409) si
     *       se intenta.</li>
     *   <li>Un usuario no puede expulsarse a si mismo por esta via (existe para que un
     *       ADMIN/OWNER saque a OTRO miembro, no como forma de "abandonar" el proyecto) —
     *       {@link SinPermisoException} (403) si {@code usuarioId == solicitanteId}.</li>
     * </ul>
     */
    @Transactional
    public void removerMiembro(Long proyectoId, Long usuarioId, Long solicitanteId) {
        if (usuarioId.equals(solicitanteId)) {
            throw new SinPermisoException(
                    "No puede expulsarse a si mismo del proyecto " + proyectoId);
        }

        MiembroProyecto miembro = miembroProyectoRepository.findByProyecto_IdAndUsuario_Id(proyectoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + usuarioId + " no es miembro del proyecto " + proyectoId));

        if (RolProyecto.ROL_OWNER.equals(miembro.getRolProyecto().getCodigo())) {
            throw new NoSePuedeExpulsarOwnerException(proyectoId);
        }

        miembroProyectoRepository.delete(miembro);
        proyectoRepository.decrementarMiembros(proyectoId);
    }

    /**
     * Recalcula {@code miembros_actuales} contra el conteo real de
     * {@code cta_miembro_proyecto} — corrige cualquier desincronizacion
     * (ej. filas borradas/insertadas fuera de {@link #agregarMiembro}).
     */
    @Transactional
    public void reconciliarContadorMiembros(Long proyectoId) {
        long conteoReal = miembroProyectoRepository.countByProyecto_Id(proyectoId);
        int filas = proyectoRepository.actualizarMiembrosActuales(proyectoId, (int) conteoReal);
        if (filas == 0) {
            throw new RecursoNoEncontradoException("No existe el proyecto " + proyectoId);
        }
    }
}
