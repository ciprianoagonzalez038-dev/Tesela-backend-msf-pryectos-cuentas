package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

/**
 * Aprovisionamiento just-in-time: resuelve el usuario LOCAL de un token del Authorization Server
 * ({@code sub} = subject externo) y, si no existe, lo crea.
 *
 * <ol>
 *   <li>Por {@code subject_externo}: si existe, es ese usuario. Nada mas.</li>
 *   <li>Sin correo en el token: 403 y NO se crea nada (el correo es necesario para invitaciones
 *       dirigidas, listado de miembros y transferencia de propiedad).</li>
 *   <li>Si el correo coincide con una cuenta local SIN subject (creada por el registro local):
 *       se VINCULA (se asigna el subject) en vez de duplicar — pero solo si el emisor afirma
 *       {@code email_verified=true} o la vinculacion sin verificar esta habilitada
 *       ({@link AprovisionamientoProperties}); de lo contrario 409. Vincular por un correo SIN
 *       verificar permitiria a quien lo registre en otro servicio heredar la cuenta ajena.</li>
 *   <li>Si no, se crea: plan FREE, {@code password_hash} NULL, ACTIVO.</li>
 * </ol>
 *
 * <p><b>Concurrencia:</b> sin bloqueos propios. Dos peticiones simultaneas del mismo {@code sub}
 * nuevo intentan el INSERT; el UNIQUE de {@code subject_externo} (y el de {@code email}) hace
 * perder a una, que atrapa la violacion y relee en una transaccion NUEVA lo que ya commiteo la
 * ganadora — "ya existe", igual que en {@code ProyectoService#agregarMiembro}. Sin
 * {@code @Transactional} en el metodo publico a proposito: la transaccion fallida debe cerrarse
 * antes de releer.</p>
 */
@Service
public class AprovisionamientoUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(AprovisionamientoUsuarioService.class);
    private static final int MAX_NOMBRE = 120;

    private final UsuarioRepository usuarioRepository;
    private final PlanRepository planRepository;
    private final AprovisionamientoProperties properties;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public AprovisionamientoUsuarioService(
            UsuarioRepository usuarioRepository,
            PlanRepository planRepository,
            AprovisionamientoProperties properties,
            PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.planRepository = planRepository;
        this.properties = properties;
        this.escritura = new TransactionTemplate(transactionManager);
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setReadOnly(true);
    }

    public IdentidadUsuario resolverOAprovisionar(
            String subject, String email, String nombre, boolean emailVerificado) {
        try {
            return escritura.execute(status -> intentar(subject, email, nombre, emailVerificado));
        } catch (DataIntegrityViolationException ex) {
            IdentidadUsuario ganadora = lectura.execute(status ->
                    usuarioRepository.findBySubjectExterno(subject).map(u -> aIdentidad(u, false)).orElse(null));
            if (ganadora != null) {
                log.info("Aprovisionamiento concurrente del mismo sub: se usa la cuenta que ya commiteo otra peticion");
                return ganadora;
            }
            throw new IdentidadRechazadaException(HttpStatus.CONFLICT,
                    "El correo ya esta asociado a otra identidad");
        }
    }

    private IdentidadUsuario intentar(String subject, String email, String nombre, boolean emailVerificado) {
        var porSubject = usuarioRepository.findBySubjectExterno(subject);
        if (porSubject.isPresent()) {
            return aIdentidad(porSubject.get(), false);
        }

        if (!StringUtils.hasText(email)) {
            throw new IdentidadRechazadaException(HttpStatus.FORBIDDEN,
                    "Tu cuenta no tiene un correo registrado. Xrello necesita un correo para operar "
                            + "(invitaciones, miembros, propiedad de proyectos): agregalo a tu cuenta e intenta de nuevo");
        }
        String correo = email.trim().toLowerCase(java.util.Locale.ROOT);

        var porEmail = usuarioRepository.findByEmail(correo);
        if (porEmail.isPresent()) {
            Usuario existente = porEmail.get();
            if (existente.getSubjectExterno() != null) {
                throw new IdentidadRechazadaException(HttpStatus.CONFLICT,
                        "El correo ya esta asociado a otra identidad");
            }
            if (!emailVerificado && !properties.isVincularPorEmailSinVerificar()) {
                log.warn("Vinculacion rechazada: existe una cuenta local con ese correo y el emisor no verifica correos");
                throw new IdentidadRechazadaException(HttpStatus.CONFLICT,
                        "Ya existe una cuenta de Xrello con ese correo. Vincularla requiere un correo verificado "
                                + "o una migracion controlada de usuarios");
            }
            exigirActivo(existente);
            existente.setSubjectExterno(subject);
            usuarioRepository.saveAndFlush(existente);
            log.info("Cuenta local vinculada a su identidad externa: usuarioId={}", existente.getId());
            return aIdentidad(existente, false);
        }

        Plan planFree = planRepository.findByCodigo(Plan.CODIGO_FREE)
                .orElseThrow(() -> new IllegalStateException("No existe el plan " + Plan.CODIGO_FREE));
        Usuario nuevo = new Usuario();
        nuevo.setSubjectExterno(subject);
        nuevo.setEmail(correo);
        nuevo.setNombre(nombreSeguro(nombre, correo));
        nuevo.setPasswordHash(null);
        nuevo.setEstado(Usuario.ESTADO_ACTIVO);
        nuevo.setPlan(planFree);
        nuevo.setEmailVerificado(emailVerificado);
        nuevo = usuarioRepository.saveAndFlush(nuevo);
        log.info("Usuario aprovisionado desde su identidad externa: usuarioId={}", nuevo.getId());
        return aIdentidad(nuevo, true);
    }

    private static IdentidadUsuario aIdentidad(Usuario usuario, boolean aprovisionado) {
        exigirActivo(usuario);
        return new IdentidadUsuario(usuario.getId(), usuario.getEmail(), usuario.getNombre(), aprovisionado);
    }

    private static void exigirActivo(Usuario usuario) {
        if (!Usuario.ESTADO_ACTIVO.equals(usuario.getEstado())) {
            throw new IdentidadRechazadaException(HttpStatus.FORBIDDEN, "La cuenta no esta activa");
        }
    }

    private static String nombreSeguro(String nombre, String correo) {
        String base = StringUtils.hasText(nombre) ? nombre.trim() : correo;
        return base.length() > MAX_NOMBRE ? base.substring(0, MAX_NOMBRE) : base;
    }
}
