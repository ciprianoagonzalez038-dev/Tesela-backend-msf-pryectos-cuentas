package com.springboot.cloud.cagr.xrello.cuentas.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.AprovisionamientoProperties;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.AprovisionamientoUsuarioService;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.IdentidadRechazadaException;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.IdentidadUsuario;

@DisplayName("AprovisionamientoUsuarioService — just-in-time, idempotencia y concurrencia (Testcontainers MySQL)")
class AprovisionamientoUsuarioServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AprovisionamientoUsuarioService servicio;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private AprovisionamientoProperties propiedades;

    @AfterEach
    void restaurarPropiedades() {
        propiedades.setVincularPorEmailSinVerificar(false);
    }

    @Test
    @DisplayName("sub nuevo con email crea el usuario: FREE, password NULL, ACTIVO, subject y correo normalizado")
    void creaUsuarioNuevo() {
        IdentidadUsuario id = servicio.resolverOAprovisionar("sub-jit-1", "  JIT.Uno@Test.com ", "Jit Uno", false);

        assertThat(id.aprovisionadoEnEstaPeticion()).isTrue();
        Usuario u = usuarioRepository.findBySubjectExterno("sub-jit-1").orElseThrow();
        assertThat(u.getId()).isEqualTo(id.usuarioId());
        assertThat(u.getEmail()).isEqualTo("jit.uno@test.com");
        assertThat(u.getNombre()).isEqualTo("Jit Uno");
        assertThat(u.getPasswordHash()).isNull();
        assertThat(u.getEstado()).isEqualTo(Usuario.ESTADO_ACTIVO);
        // getId() no inicializa el proxy lazy (getCodigo() si, y falla fuera de sesion)
        assertThat(u.getPlan().getId()).isEqualTo(planRepository.findByCodigo(Plan.CODIGO_FREE).orElseThrow().getId());
        assertThat(u.getEmailVerificado()).isFalse();
    }

    @Test
    @DisplayName("sin email da 403 y no crea nada")
    void sinEmail() {
        long antes = usuarioRepository.count();

        assertThatThrownBy(() -> servicio.resolverOAprovisionar("sub-jit-sin-email", null, "Sin Email", false))
                .isInstanceOf(IdentidadRechazadaException.class)
                .satisfies(ex -> assertThat(((IdentidadRechazadaException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> servicio.resolverOAprovisionar("sub-jit-sin-email", "   ", "Sin Email", false))
                .isInstanceOf(IdentidadRechazadaException.class);

        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(usuarioRepository.findBySubjectExterno("sub-jit-sin-email")).isEmpty();
    }

    @Test
    @DisplayName("es idempotente: el mismo sub devuelve la misma cuenta, ya no como 'aprovisionada ahora'")
    void idempotente() {
        IdentidadUsuario primera = servicio.resolverOAprovisionar("sub-jit-idem", "idem@test.com", "Idem", false);
        IdentidadUsuario segunda = servicio.resolverOAprovisionar("sub-jit-idem", "idem@test.com", "Idem", false);

        assertThat(segunda.usuarioId()).isEqualTo(primera.usuarioId());
        assertThat(primera.aprovisionadoEnEstaPeticion()).isTrue();
        assertThat(segunda.aprovisionadoEnEstaPeticion()).isFalse();
    }

    @Test
    @DisplayName("10 hilos con el MISMO sub nuevo crean UN solo usuario y todos reciben su id")
    void concurrenciaCreaUnSoloUsuario() throws Exception {
        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<IdentidadUsuario>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            Callable<IdentidadUsuario> tarea = () -> {
                salida.await();
                return servicio.resolverOAprovisionar("sub-jit-concurrente", "concurrente@test.com", "Concurrente", false);
            };
            futuros.add(pool.submit(tarea));
        }
        salida.countDown();

        List<Long> ids = new ArrayList<>();
        for (Future<IdentidadUsuario> f : futuros) {
            ids.add(f.get().usuarioId());
        }
        pool.shutdown();

        assertThat(ids).as("todos los hilos obtienen la misma cuenta").allMatch(id -> id.equals(ids.get(0)));
        long cuentas = usuarioRepository.findAll().stream().filter(u -> "sub-jit-concurrente".equals(u.getSubjectExterno())).count();
        assertThat(cuentas).as("una sola fila para ese subject").isEqualTo(1);
        assertThat(usuarioRepository.findByEmail("concurrente@test.com")).isPresent();
    }

    @Test
    @DisplayName("email coincidente con una cuenta local sin subject: por defecto 409 y NO vincula (correo sin verificar)")
    void noVinculaSinVerificar() {
        Usuario local = usuarioLocal("local-sin-verificar@test.com");

        assertThatThrownBy(() -> servicio.resolverOAprovisionar("sub-jit-v1", "local-sin-verificar@test.com", "X", false))
                .isInstanceOf(IdentidadRechazadaException.class)
                .satisfies(ex -> assertThat(((IdentidadRechazadaException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(usuarioRepository.findById(local.getId()).orElseThrow().getSubjectExterno()).isNull();
    }

    @Test
    @DisplayName("email coincidente + email_verified=true: lo VINCULA sin duplicar (la transicion de los usuarios actuales)")
    void vinculaConCorreoVerificado() {
        Usuario local = usuarioLocal("local-verificado@test.com");
        long antes = usuarioRepository.count();

        IdentidadUsuario id = servicio.resolverOAprovisionar("sub-jit-v2", "Local-Verificado@test.com", "X", true);

        assertThat(id.usuarioId()).isEqualTo(local.getId());
        assertThat(id.aprovisionadoEnEstaPeticion()).as("ya existia: no es una cuenta creada ahora").isFalse();
        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(usuarioRepository.findById(local.getId()).orElseThrow().getSubjectExterno()).isEqualTo("sub-jit-v2");
    }

    @Test
    @DisplayName("con vincular-por-email-sin-verificar=true (migracion controlada) vincula aunque el emisor no verifique")
    void vinculaConLaBanderaDeMigracion() {
        propiedades.setVincularPorEmailSinVerificar(true);
        Usuario local = usuarioLocal("local-bandera@test.com");

        IdentidadUsuario id = servicio.resolverOAprovisionar("sub-jit-v3", "local-bandera@test.com", "X", false);

        assertThat(id.usuarioId()).isEqualTo(local.getId());
        assertThat(usuarioRepository.findById(local.getId()).orElseThrow().getSubjectExterno()).isEqualTo("sub-jit-v3");
    }

    @Test
    @DisplayName("el correo de una cuenta ya vinculada a OTRO subject es 409")
    void correoDeCuentaYaVinculada() {
        servicio.resolverOAprovisionar("sub-jit-dueno", "ya-vinculado@test.com", "Dueno", false);

        assertThatThrownBy(() -> servicio.resolverOAprovisionar("sub-jit-otro", "ya-vinculado@test.com", "Otro", true))
                .isInstanceOf(IdentidadRechazadaException.class)
                .satisfies(ex -> assertThat(((IdentidadRechazadaException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("una cuenta no activa no entra (403), ni por subject ni al vincular")
    void cuentaNoActiva() {
        servicio.resolverOAprovisionar("sub-jit-susp", "susp-jit@test.com", "Susp", false);
        Usuario u = usuarioRepository.findBySubjectExterno("sub-jit-susp").orElseThrow();
        u.setEstado(Usuario.ESTADO_SUSPENDIDO);
        usuarioRepository.save(u);

        assertThatThrownBy(() -> servicio.resolverOAprovisionar("sub-jit-susp", "susp-jit@test.com", "Susp", false))
                .isInstanceOf(IdentidadRechazadaException.class)
                .satisfies(ex -> assertThat(((IdentidadRechazadaException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Usuario usuarioLocal(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash("hash-de-prueba");
        u.setNombre("Local");
        u.setEstado(Usuario.ESTADO_ACTIVO);
        u.setPlan(planRepository.findByCodigo(Plan.CODIGO_FREE).orElseThrow());
        u.setEmailVerificado(false);
        return usuarioRepository.save(u);
    }
}
