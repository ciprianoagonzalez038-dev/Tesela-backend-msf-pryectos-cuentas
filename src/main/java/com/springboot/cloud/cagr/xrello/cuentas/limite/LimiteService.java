package com.springboot.cloud.cagr.xrello.cuentas.limite;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.xrello.cuentas.domain.LimiteUsuario;
import com.springboot.cloud.cagr.xrello.cuentas.domain.PlanLimite;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.exception.LimiteExcedidoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.LimiteNoConfiguradoException;
import com.springboot.cloud.cagr.xrello.cuentas.repository.LimiteUsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.MiembroProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanLimiteRepository;

/**
 * Resuelve y verifica los limites de cupo de un usuario (ver {@link CodigosLimite}).
 *
 * <p>Solo los proyectos con {@code estado = ACTIVO} cuentan para cualquier
 * limite — un proyecto archivado ({@link Proyecto#ESTADO_ARCHIVADO}) libera
 * su cupo (ver XRELLO_CONTRATO.md sobre constantes de dominio).</p>
 */
@Service
public class LimiteService {

    private final LimiteUsuarioRepository limiteUsuarioRepository;
    private final PlanLimiteRepository planLimiteRepository;
    private final MiembroProyectoRepository miembroProyectoRepository;

    public LimiteService(
            LimiteUsuarioRepository limiteUsuarioRepository,
            PlanLimiteRepository planLimiteRepository,
            MiembroProyectoRepository miembroProyectoRepository) {
        this.limiteUsuarioRepository = limiteUsuarioRepository;
        this.planLimiteRepository = planLimiteRepository;
        this.miembroProyectoRepository = miembroProyectoRepository;
    }

    /**
     * Resuelve el valor de un limite para un usuario, con precedencia:
     * <ol>
     *   <li>{@code cta_limite_usuario} del usuario, si existe fila (incluso con
     *       {@code valor = NULL} — esa fila SI cuenta como "configurado, ilimitado").</li>
     *   <li>{@code cta_plan_limite} del plan del usuario.</li>
     *   <li>Si no hay ninguna de las dos, {@link LimiteNoConfiguradoException}.</li>
     * </ol>
     *
     * @return el valor maximo, o {@code null} si el limite resuelto es ilimitado.
     */
    @Transactional(readOnly = true)
    public Integer resolver(Long usuarioId, String codigoLimite) {
        // OJO: Optional.map() trata un valor NULL devuelto por la funcion como "ausente", no
        // como "presente con valor null" -- encadenar .map(...).or(...) aqui haria que una fila
        // de override con valor=NULL (ilimitado) cayera incorrectamente al valor del plan, en
        // vez de ganar como corresponde. Por eso se verifica isPresent() explicitamente en cada
        // nivel, sin pasar por .map() para extraer el valor final.
        Optional<LimiteUsuario> override = limiteUsuarioRepository.findByUsuario_IdAndLimite_Codigo(usuarioId, codigoLimite);
        if (override.isPresent()) {
            return override.get().getValor();
        }

        Optional<PlanLimite> planLimite = planLimiteRepository.findByUsuarioIdAndLimiteCodigo(usuarioId, codigoLimite);
        if (planLimite.isPresent()) {
            return planLimite.get().getValor();
        }

        throw new LimiteNoConfiguradoException(usuarioId, codigoLimite);
    }

    /**
     * Verifica que el usuario todavia tenga cupo para poseer un proyecto ACTIVO
     * mas como owner. Usada tanto por {@code crearProyecto} como por
     * {@code transferirPropiedad} (el destinatario de la transferencia pasa a
     * ser owner de un proyecto mas).
     */
    @Transactional(readOnly = true)
    public void verificarPuedeSerOwnerDeUnProyectoMas(Long usuarioId) {
        long usoActual = miembroProyectoRepository.countByUsuario_IdAndRolProyecto_CodigoAndProyecto_Estado(
                usuarioId, RolProyecto.ROL_OWNER, Proyecto.ESTADO_ACTIVO);
        verificarLimite(usuarioId, CodigosLimite.PROYECTOS_COMO_OWNER, usoActual);
    }

    /**
     * Verifica ambos limites que aplican a CREAR un proyecto nuevo:
     * {@code PROYECTOS_COMO_OWNER} (especifico) y {@code PROYECTOS_TOTAL}
     * (agregado owner + invitado).
     */
    @Transactional(readOnly = true)
    public void verificarPuedeCrearProyecto(Long usuarioId) {
        verificarPuedeSerOwnerDeUnProyectoMas(usuarioId);
        long totalActivos = miembroProyectoRepository.countByUsuario_IdAndProyecto_Estado(
                usuarioId, Proyecto.ESTADO_ACTIVO);
        verificarLimite(usuarioId, CodigosLimite.PROYECTOS_TOTAL, totalActivos);
    }

    /**
     * Verifica ambos limites que aplican a ACEPTAR una invitacion (unirse como
     * no-owner): {@code PROYECTOS_COMO_INVITADO} y {@code PROYECTOS_TOTAL}.
     *
     * <p>SIN {@code @Transactional} propio, a proposito — probado en caliente: incluso sin
     * {@code noRollbackFor}, {@code @Transactional(readOnly=true)} aqui marca la transaccion
     * COMPARTIDA de {@code aceptarInvitacion} como rollback-only en cuanto lanza
     * {@link LimiteExcedidoException}, pase lo que pase en el llamador — resultado:
     * {@code UnexpectedRollbackException} al commitear {@code RegistroService#registrar},
     * aunque ESE ya haya capturado la excepcion. Sin boundary propio, este metodo simplemente
     * participa en la transaccion de {@code aceptarInvitacion} y no puede marcar nada por su
     * cuenta.</p>
     */
    public void verificarPuedeAceptarInvitacion(Long usuarioId) {
        long comoInvitadoActivos = miembroProyectoRepository
                .countByUsuario_IdAndRolProyecto_CodigoNotAndProyecto_Estado(
                        usuarioId, RolProyecto.ROL_OWNER, Proyecto.ESTADO_ACTIVO);
        verificarLimite(usuarioId, CodigosLimite.PROYECTOS_COMO_INVITADO, comoInvitadoActivos);

        long totalActivos = miembroProyectoRepository.countByUsuario_IdAndProyecto_Estado(
                usuarioId, Proyecto.ESTADO_ACTIVO);
        verificarLimite(usuarioId, CodigosLimite.PROYECTOS_TOTAL, totalActivos);
    }

    private void verificarLimite(Long usuarioId, String codigoLimite, long usoActual) {
        Integer limite = resolver(usuarioId, codigoLimite);
        if (limite != null && usoActual >= limite) {
            throw new LimiteExcedidoException(codigoLimite, limite, usoActual);
        }
    }
}
