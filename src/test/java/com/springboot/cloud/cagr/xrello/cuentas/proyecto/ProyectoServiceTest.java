package com.springboot.cloud.cagr.xrello.cuentas.proyecto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Limite;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.PlanLimite;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Proyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CupoProyectoLlenoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.LimiteExcedidoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.NoSePuedeExpulsarOwnerException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.SinPermisoException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.YaEsMiembroException;
import com.springboot.cloud.cagr.xrello.cuentas.limite.CodigosLimite;
import com.springboot.cloud.cagr.xrello.cuentas.repository.LimiteRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.MiembroProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanLimiteRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.ProyectoRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

import jakarta.persistence.EntityManagerFactory;

@DisplayName("ProyectoService — tests de integracion (Testcontainers MySQL)")
class ProyectoServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProyectoService proyectoService;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private LimiteRepository limiteRepository;

    @Autowired
    private PlanLimiteRepository planLimiteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MiembroProyectoRepository miembroProyectoRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static int contadorPrefijo = 0;

    @Test
    @DisplayName("Crear un proyecto por encima del limite PROYECTOS_COMO_OWNER lanza LimiteExcedidoException")
    void crearProyectoPorEncimaDelLimiteLanzaExcepcion() {
        // El plan FREE sembrado en V2 da PROYECTOS_COMO_OWNER = 5.
        Usuario owner = nuevoUsuario("owner-limite@test.com", planFree());
        for (int i = 0; i < 5; i++) {
            proyectoService.crearProyecto(owner.getId(), "Proyecto " + i, null, prefijoUnico());
        }

        assertThatThrownBy(() -> proyectoService.crearProyecto(owner.getId(), "Sexto", null, prefijoUnico()))
                .isInstanceOf(LimiteExcedidoException.class)
                .satisfies(ex -> assertThat(((LimiteExcedidoException) ex).getCodigoLimite())
                        .isEqualTo(CodigosLimite.PROYECTOS_COMO_OWNER));
    }

    @Test
    @DisplayName("Un prefijo que no empieza con una letra lanza IllegalArgumentException (CuentasExceptionHandler lo traduce a 400, no al 500 "
            + "generico de ms-commons — reportado 2026-11-14: el asistente de 'Nuevo proyecto' sugeria un prefijo asi y el alta respondia error 500)")
    void prefijoQueEmpiezaConDigitoLanzaIllegalArgumentException() {
        Usuario owner = nuevoUsuario("owner-prefijo-invalido@test.com", planFree());

        assertThatThrownBy(() -> proyectoService.crearProyecto(owner.getId(), "Proyecto con prefijo invalido", null, "1AB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1AB");
    }

    @Test
    @DisplayName("Un proyecto archivado no consume cupo de PROYECTOS_COMO_OWNER")
    void proyectoArchivadoNoConsumeCupo() {
        Usuario owner = nuevoUsuario("owner-archivado@test.com", planFree());
        List<Proyecto> proyectos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            proyectos.add(proyectoService.crearProyecto(owner.getId(), "Proyecto " + i, null, prefijoUnico()));
        }

        // Al limite (5/5): un sexto proyecto debe fallar.
        assertThatThrownBy(() -> proyectoService.crearProyecto(owner.getId(), "Sexto", null, prefijoUnico()))
                .isInstanceOf(LimiteExcedidoException.class);

        // Archivar uno libera el cupo: ahora el sexto SI debe poder crearse.
        proyectoService.archivarProyecto(proyectos.get(0).getId());

        Proyecto sexto = proyectoService.crearProyecto(owner.getId(), "Sexto", null, prefijoUnico());
        assertThat(sexto.getId()).isNotNull();
    }

    @Test
    @DisplayName("MIEMBROS_POR_PROYECTO se resuelve contra el plan del OWNER, nunca contra el del invitado")
    void limiteDeMiembrosUsaElPlanDelOwnerNoDelInvitado() {
        // Plan del owner: MIEMBROS_POR_PROYECTO=1 (el owner ya ocupa ese unico cupo al crear).
        Plan planRestrictivo = nuevoPlanConLimites("RESTRICTIVO_MIEMBROS", Map.of(
                CodigosLimite.PROYECTOS_COMO_OWNER, 100,
                CodigosLimite.PROYECTOS_COMO_INVITADO, 100,
                CodigosLimite.PROYECTOS_TOTAL, 100,
                CodigosLimite.MIEMBROS_POR_PROYECTO, 1));
        Usuario owner = nuevoUsuario("owner-restrictivo@test.com", planRestrictivo);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Restrictivo", null, prefijoUnico());

        // El invitado esta en el plan FREE, cuyo MIEMBROS_POR_PROYECTO es 8 -- si la regla
        // usara (incorrectamente) el plan del invitado, esto tendria que tener exito.
        Usuario invitado = nuevoUsuario("invitado-plan-amplio@test.com", planFree());

        assertThatThrownBy(() -> proyectoService.agregarMiembro(
                proyecto.getId(), invitado.getId(), RolProyecto.ROL_MIEMBRO, owner.getId()))
                .isInstanceOf(CupoProyectoLlenoException.class);
    }

    @Test
    @DisplayName("agregarMiembro dos veces con el mismo usuario lanza YaEsMiembroException sin dejar el contador incrementado")
    void agregarMiembroDuplicadoLanzaYaEsMiembroSinIncrementarContador() {
        Usuario owner = nuevoUsuario("owner-duplicado@test.com", planFree());
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Duplicado", null, prefijoUnico());
        Usuario invitado = nuevoUsuario("invitado-duplicado@test.com", planFree());

        proyectoService.agregarMiembro(proyecto.getId(), invitado.getId(), RolProyecto.ROL_MIEMBRO, owner.getId());
        int contadorTrasPrimeraVez = proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales();
        assertThat(contadorTrasPrimeraVez).isEqualTo(2);

        assertThatThrownBy(() -> proyectoService.agregarMiembro(
                proyecto.getId(), invitado.getId(), RolProyecto.ROL_MIEMBRO, owner.getId()))
                .isInstanceOf(YaEsMiembroException.class);

        int contadorTrasIntentoDuplicado = proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales();
        assertThat(contadorTrasIntentoDuplicado).isEqualTo(2);
    }

    @Test
    @DisplayName("CONCURRENCIA: 10 hilos en agregarMiembro sobre 2 cupos libres -> exactamente 2 exitos y 8 CupoProyectoLlenoException")
    void concurrenciaAgregarMiembroRespetaElCupo() throws InterruptedException {
        // MIEMBROS_POR_PROYECTO=3: el owner ya ocupa 1 al crear -> quedan 2 cupos libres.
        Plan planLimitado = nuevoPlanConLimites("CONCURRENCIA_CUPO", Map.of(
                CodigosLimite.PROYECTOS_COMO_OWNER, 100,
                CodigosLimite.PROYECTOS_COMO_INVITADO, 100,
                CodigosLimite.PROYECTOS_TOTAL, 100,
                CodigosLimite.MIEMBROS_POR_PROYECTO, 3));
        Usuario owner = nuevoUsuario("owner-concurrencia-cupo@test.com", planLimitado);
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Concurrencia cupo", null, prefijoUnico());

        int hilos = 10;
        List<Usuario> invitados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            invitados.add(nuevoUsuario("invitado-concurrencia-" + i + "@test.com", planFree()));
        }

        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger cupoLleno = new AtomicInteger();
        List<Throwable> inesperadas = Collections.synchronizedList(new ArrayList<>());

        ejecutarConcurrentemente(invitados, hilos, invitado -> {
            try {
                proyectoService.agregarMiembro(proyecto.getId(), invitado.getId(), RolProyecto.ROL_MIEMBRO, owner.getId());
                exitos.incrementAndGet();
            } catch (CupoProyectoLlenoException ex) {
                cupoLleno.incrementAndGet();
            } catch (Throwable ex) {
                inesperadas.add(ex);
            }
        });

        assertThat(inesperadas).isEmpty();
        assertThat(exitos.get()).isEqualTo(2);
        assertThat(cupoLleno.get()).isEqualTo(8);

        Proyecto recargado = proyectoRepository.findById(proyecto.getId()).orElseThrow();
        assertThat(recargado.getMiembrosActuales()).isEqualTo(3);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("reconciliarContadorMiembros corrige un contador desincronizado a mano")
    void reconciliarContadorMiembrosCorrigeDesincronizacion() {
        // @Transactional en el propio metodo de test: actualizarMiembrosActuales() es un
        // @Modifying nativo y exige una transaccion activa para ejecutarse -- en produccion
        // siempre corre dentro de un metodo @Transactional de ProyectoService; aqui se llama
        // directo desde el test para simular la corrupcion, asi que el test necesita su propia
        // transaccion (con join automatico de las @Transactional de ProyectoService, misma
        // PlatformTransactionManager).
        Usuario owner = nuevoUsuario("owner-reconciliar@test.com", planFree());
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Reconciliar", null, prefijoUnico());

        // Desincroniza el contador a mano, simulando un dato corrupto (nunca via agregarMiembro).
        proyectoRepository.actualizarMiembrosActuales(proyecto.getId(), 999);
        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales()).isEqualTo(999);

        proyectoService.reconciliarContadorMiembros(proyecto.getId());

        // Solo el owner es miembro real de este proyecto -> el conteo real es 1.
        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales()).isEqualTo(1);
    }

    @Test
    @DisplayName("removerMiembro sobre el OWNER lanza NoSePuedeExpulsarOwnerException (409) y no altera miembros_actuales")
    void removerMiembroSobreOwnerLanza409YNoAlteraContador() {
        Usuario owner = nuevoUsuario("owner-expulsion@test.com", planFree());
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Expulsion owner", null, prefijoUnico());
        Usuario admin = nuevoUsuario("admin-expulsion@test.com", planFree());
        proyectoService.agregarMiembro(proyecto.getId(), admin.getId(), RolProyecto.ROL_ADMIN, owner.getId());

        assertThatThrownBy(() -> proyectoService.removerMiembro(proyecto.getId(), owner.getId(), admin.getId()))
                .isInstanceOf(NoSePuedeExpulsarOwnerException.class);

        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales()).isEqualTo(2);
        assertThat(miembroProyectoRepository.findByProyecto_IdAndUsuario_Id(proyecto.getId(), owner.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("removerMiembro con el propio usuario como solicitante lanza SinPermisoException (no puede autoexpulsarse)")
    void removerMiembroAutoexpulsionLanzaSinPermiso() {
        Usuario owner = nuevoUsuario("owner-autoexpulsion@test.com", planFree());
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Autoexpulsion", null, prefijoUnico());
        Usuario miembro = nuevoUsuario("miembro-autoexpulsion@test.com", planFree());
        proyectoService.agregarMiembro(proyecto.getId(), miembro.getId(), RolProyecto.ROL_MIEMBRO, owner.getId());

        assertThatThrownBy(() -> proyectoService.removerMiembro(proyecto.getId(), miembro.getId(), miembro.getId()))
                .isInstanceOf(SinPermisoException.class);

        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales()).isEqualTo(2);
    }

    @Test
    @DisplayName("removerMiembro sobre un miembro normal decrementa miembros_actuales en exactamente 1")
    void removerMiembroNormalDecrementaContadorEnUno() {
        Usuario owner = nuevoUsuario("owner-remocion-normal@test.com", planFree());
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Remocion normal", null, prefijoUnico());
        Usuario miembro = nuevoUsuario("miembro-remocion-normal@test.com", planFree());
        proyectoService.agregarMiembro(proyecto.getId(), miembro.getId(), RolProyecto.ROL_MIEMBRO, owner.getId());
        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales()).isEqualTo(2);

        proyectoService.removerMiembro(proyecto.getId(), miembro.getId(), owner.getId());

        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getMiembrosActuales()).isEqualTo(1);
        assertThat(miembroProyectoRepository.findByProyecto_IdAndUsuario_Id(proyecto.getId(), miembro.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("listarProyectosDeUsuario ejecuta UNA sola consulta SQL (sin N+1)")
    void listarProyectosDeUsuarioEjecutaUnaSolaConsulta() {
        Usuario owner = nuevoUsuario("owner-listar@test.com", planFree());
        proyectoService.crearProyecto(owner.getId(), "Proyecto A", null, prefijoUnico());
        proyectoService.crearProyecto(owner.getId(), "Proyecto B", null, prefijoUnico());
        proyectoService.crearProyecto(owner.getId(), "Proyecto C", null, prefijoUnico());

        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        List<ProyectoConRolView> resultado = proyectoService.listarProyectosDeUsuario(owner.getId());

        assertThat(resultado).hasSize(3);
        assertThat(resultado).allMatch(ProyectoConRolView::esOwner);
        assertThat(estadisticas.getQueryExecutionCount())
                .as("listarProyectosDeUsuario debe ejecutar exactamente 1 consulta SQL")
                .isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Ejecuta una accion sobre cada elemento en su propio hilo, todos arrancando a la vez. */
    private <T> void ejecutarConcurrentemente(List<T> elementos, int hilos, java.util.function.Consumer<T> accion)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);

        List<Future<?>> futuros = new ArrayList<>();
        for (T elemento : elementos) {
            futuros.add(executor.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                accion.accept(elemento);
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
    }

    @Test
    @DisplayName("transferirPropiedadPorCodigo da el MISMO mensaje para email inexistente y para email no miembro")
    void transferirPropiedadMismoMensajeParaEmailInexistenteYNoMiembro() {
        // Regresion del enumeration leak encontrado en auditoria (ver XRELLO_CONTRATO.md §18):
        // antes del fix, "no existe un usuario con ese email" y "el usuario existe pero no es
        // miembro" daban mensajes DISTINTOS -- un OWNER podia usar esto para enumerar que correos
        // tienen cuenta en la plataforma.
        Usuario owner = nuevoUsuario("owner-transferir@test.com", planFree());
        Proyecto proyecto = proyectoService.crearProyecto(owner.getId(), "Proyecto Transferir", null, prefijoUnico());

        // Caso 1: el email NO tiene ninguna cuenta registrada.
        Throwable errorEmailInexistente = org.assertj.core.api.Assertions.catchThrowable(() ->
                proyectoService.transferirPropiedadPorCodigo(proyecto.getCodigo(), "nadie-registrado@test.com"));

        // Caso 2: el email SI tiene cuenta, pero no es miembro de ESTE proyecto.
        nuevoUsuario("existe-pero-no-es-miembro@test.com", planFree());
        Throwable errorNoMiembro = org.assertj.core.api.Assertions.catchThrowable(() ->
                proyectoService.transferirPropiedadPorCodigo(proyecto.getCodigo(), "existe-pero-no-es-miembro@test.com"));

        assertThat(errorEmailInexistente).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(errorNoMiembro).isInstanceOf(RecursoNoEncontradoException.class);
        // Mismo mensaje EXACTO en ambos casos -- y sin repetir el email recibido (ver
        // XRELLO_CONTRATO.md §18 y el Javadoc de transferirPropiedadPorCodigo): ni la respuesta
        // ni el log de la aplicacion deben conservar el email probado.
        String mensajeEsperado = "El usuario indicado no es miembro de este proyecto";
        assertThat(((RecursoNoEncontradoException) errorEmailInexistente).getMessage()).isEqualTo(mensajeEsperado);
        assertThat(((RecursoNoEncontradoException) errorNoMiembro).getMessage()).isEqualTo(mensajeEsperado);
    }

    private Plan planFree() {
        return planRepository.findAll().stream()
                .filter(p -> "FREE".equals(p.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El plan FREE deberia existir (V2 seed)"));
    }

    /** Crea un plan nuevo con valores de limite propios (para aislar un codigo bajo prueba). */
    private Plan nuevoPlanConLimites(String codigoPlan, Map<String, Integer> valoresPorCodigoLimite) {
        Plan plan = new Plan();
        plan.setCodigo(codigoPlan + "-" + System.nanoTime());
        plan.setNombre(codigoPlan);
        plan.setActivo(true);
        plan = planRepository.save(plan);

        for (Map.Entry<String, Integer> entrada : valoresPorCodigoLimite.entrySet()) {
            Limite limite = limitePorCodigo(entrada.getKey());
            PlanLimite planLimite = new PlanLimite();
            planLimite.setPlan(plan);
            planLimite.setLimite(limite);
            planLimite.setValor(entrada.getValue());
            planLimiteRepository.save(planLimite);
        }
        return plan;
    }

    private Limite limitePorCodigo(String codigo) {
        return limiteRepository.findAll().stream()
                .filter(l -> codigo.equals(l.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El limite " + codigo + " deberia existir (V2 seed)"));
    }

    private Usuario nuevoUsuario(String email, Plan plan) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash("hash-de-prueba");
        usuario.setNombre("Usuario de prueba");
        usuario.setEstado(Usuario.ESTADO_ACTIVO);
        usuario.setPlan(plan);
        usuario.setEmailVerificado(false);
        return usuarioRepository.save(usuario);
    }

    /** Prefijo corto y unico por llamada, valido contra {@code ^[A-Z][A-Z0-9]{1,9}$}. */
    private static synchronized String prefijoUnico() {
        contadorPrefijo++;
        return "P" + contadorPrefijo;
    }
}
