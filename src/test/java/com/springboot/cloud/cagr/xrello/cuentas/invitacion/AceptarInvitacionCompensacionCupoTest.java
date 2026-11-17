package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.LimiteExcedidoException;
import com.springboot.cloud.cagr.xrello.cuentas.proyecto.ProyectoService;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.InvitacionUsoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

/**
 * Tarea 0 del contrato — prueba el gap de compensacion que {@code liberarUso} por si solo NO
 * cubre: si {@code agregarMiembro} ya tuvo exito (incremento el cupo del proyecto) y DESPUES
 * ocurre un rechazo que sobrevive ({@code noRollbackFor}), el cupo debe liberarse tambien, no
 * solo el uso de la invitacion.
 *
 * <p>Hoy, con el reordenamiento de la Tarea 1, la UNICA excepcion real que puede ocurrir despues
 * de {@code agregarMiembro} es {@link com.springboot.cloud.cagr.xrello.cuentas.exception.YaEsMiembroException}
 * (duplicado en {@code cta_invitacion_uso}), que siempre revierte TODO por si sola (flush
 * fallido, ver Javadoc de {@code InvitacionService#aceptarInvitacion}) — asi que el gap no es
 * alcanzable con datos reales. Esta clase lo fuerza con un spy sobre
 * {@link InvitacionUsoRepository#saveAndFlush} para que lance una {@link LimiteExcedidoException}
 * DIRECTAMENTE (sin tocar la base, sin flush real) en el mismo punto donde hoy iria el insert de
 * uso — simulando el escenario estructural: una {@code CuentasException} que sobrevive,
 * ocurriendo DESPUES de que el cupo ya se ocupo.</p>
 */
@DisplayName("aceptarInvitacion — compensacion del cupo cuando el rechazo ocurre DESPUES de agregarMiembro (Tarea 0)")
class AceptarInvitacionCompensacionCupoTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private InvitacionUsoRepository invitacionUsoRepository;

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

    @Test
    @DisplayName("Un rechazo forzado DESPUES de agregarMiembro libera el cupo ocupado, no solo el uso de la invitacion")
    void rechazoPosteriorAAgregarMiembroLiberaCupoYUso() {
        Usuario owner = nuevoUsuario("owner-compensacion-cupo@test.com");
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Proyecto Compensacion Cupo", null, "CCC");
        assertThat(proyecto.getMiembrosActuales()).isEqualTo(1);

        InvitacionCreada creada = invitacionService.crearInvitacion(
                proyecto.getCodigo(), Invitacion.TIPO_ENLACE_ABIERTO, null, "MIEMBRO", 7, 5, owner.getId());

        // Simula una CuentasException que sobrevive (noRollbackFor) ocurriendo DESPUES de que
        // agregarMiembro YA incremento miembros_actuales -- sin pasar por un flush real, para
        // que no sea el "accidente" de YaEsMiembroException el que revierta todo.
        doThrow(new LimiteExcedidoException("LIMITE_DE_PRUEBA", 0, 0))
                .when(invitacionUsoRepository).saveAndFlush(any());

        Usuario invitado = nuevoUsuario("invitado-compensacion-cupo@test.com");
        assertThatThrownBy(() -> invitacionService.aceptarInvitacion(creada.tokenEnClaro(), invitado.getId(), false))
                .isInstanceOf(LimiteExcedidoException.class);

        // El uso de la invitacion se libero (comportamiento ya existente, liberarUso).
        Invitacion recargada = invitacionRepository.findByCodigo(creada.invitacion().getCodigo()).orElseThrow();
        assertThat(recargada.getUsosActuales()).isEqualTo(0);

        // El cupo del proyecto TAMBIEN se libero -- esta es la correccion de la Tarea 0: sin
        // ella, miembros_actuales quedaria en 2 (el incremento de agregarMiembro nunca se
        // deshace) pese a que el invitado nunca quedo como miembro real del proyecto.
        Proyecto proyectoRecargado = proyectoService.obtenerPorCodigo(proyecto.getCodigo());
        assertThat(proyectoRecargado.getMiembrosActuales()).isEqualTo(1);
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
}
