package com.springboot.cloud.cagr.xrello.cuentas.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.springboot.cloud.cagr.xrello.cuentas.AbstractIntegrationTest;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Limite;
import com.springboot.cloud.cagr.xrello.cuentas.domain.LimiteUsuario;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.LimiteNoConfiguradoException;
import com.springboot.cloud.cagr.xrello.cuentas.repository.LimiteRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.LimiteUsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.PlanRepository;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

@DisplayName("LimiteService — tests de integracion (Testcontainers MySQL)")
class LimiteServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LimiteService limiteService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private LimiteRepository limiteRepository;

    @Autowired
    private LimiteUsuarioRepository limiteUsuarioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Sin override, resuelve el valor del plan; con override en cta_limite_usuario, ese gana")
    void overrideDeUsuarioGanaSobrePlan() {
        Plan free = planFree();
        Usuario usuario = nuevoUsuario("precedencia@test.com", free);
        Limite limite = limitePorCodigo(CodigosLimite.PROYECTOS_COMO_OWNER);

        // Sin override: el valor sembrado del plan FREE para este codigo es 5 (ver V2 seed).
        assertThat(limiteService.resolver(usuario.getId(), CodigosLimite.PROYECTOS_COMO_OWNER)).isEqualTo(5);

        // Con override explicito: debe ganar el valor de cta_limite_usuario (2), no el del plan.
        LimiteUsuario override = new LimiteUsuario();
        override.setUsuario(usuario);
        override.setLimite(limite);
        override.setValor(2);
        limiteUsuarioRepository.save(override);

        assertThat(limiteService.resolver(usuario.getId(), CodigosLimite.PROYECTOS_COMO_OWNER)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un override con valor NULL significa ilimitado y tambien gana sobre el plan")
    void overrideNuloEsIlimitado() {
        Usuario usuario = nuevoUsuario("ilimitado@test.com", planFree());
        Limite limite = limitePorCodigo(CodigosLimite.MIEMBROS_POR_PROYECTO);

        LimiteUsuario override = new LimiteUsuario();
        override.setUsuario(usuario);
        override.setLimite(limite);
        override.setValor(null);
        limiteUsuarioRepository.save(override);

        assertThat(limiteService.resolver(usuario.getId(), CodigosLimite.MIEMBROS_POR_PROYECTO)).isNull();
    }

    @Test
    @DisplayName("Sin override ni valor de plan para el codigo, lanza LimiteNoConfiguradoException")
    void limiteNoConfiguradoLanzaExcepcion() {
        Usuario usuario = nuevoUsuario("sin-limite@test.com", planFree());

        assertThatThrownBy(() -> limiteService.resolver(usuario.getId(), "CODIGO_QUE_NO_EXISTE"))
                .isInstanceOf(LimiteNoConfiguradoException.class);
    }

    private Plan planFree() {
        return planRepository.findAll().stream()
                .filter(p -> "FREE".equals(p.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El plan FREE deberia existir (V2 seed)"));
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
}
