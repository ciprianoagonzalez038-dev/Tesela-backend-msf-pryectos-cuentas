package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.springboot.cloud.cagr.xrello.cuentas.exception.EmailYaRegistradoException;

/**
 * Doble de prueba de {@link UsuarioDirectorioPort}, activo SOLO en el perfil {@code test} (ver
 * {@code AbstractIntegrationTest}, {@code @ActiveProfiles("test")}) — sin Eureka ni red real en
 * ese entorno, así que el adapter HTTP real ({@link UsuarioDirectorioClient}, {@code @Profile("!test")})
 * no puede resolver {@code http://ms-usuario}. Guarda las identidades EN MEMORIA, por contexto de
 * Spring (se reinicia con cada contexto), igual de espíritu que {@code StaticTestConfigServiceImpl}/
 * {@code StaticTestCatalogServiceImpl} de ms-onboarding.
 *
 * <p><b>El contador de ids NO puede empezar siempre en el mismo valor fijo</b> — cada clase de
 * test de este módulo arranca su PROPIO contexto de Spring (y por tanto su propia instancia de
 * este bean, con el contador en cero), pero TODAS comparten el MISMO contenedor MySQL singleton
 * (ver {@code AbstractIntegrationTest}) sin limpiar filas entre clases. Con un contador fijo,
 * dos clases de test generan el MISMO {@code subject_externo} para usuarios DISTINTOS →
 * {@code DataIntegrityViolationException} en el segundo INSERT → el camino de "aprovisionamiento
 * concurrente" de {@code AprovisionamientoUsuarioService} relee por {@code subject_externo} y
 * devuelve SILENCIOSAMENTE la fila de la OTRA clase (misma subject, usuario equivocado) — un
 * usuario fantasma: la petición HTTP responde 201 con un token valido, pero
 * {@code usuarioRepository.findByEmail(<el correo que se quiso registrar>)} queda vacío.
 * Verificado en caliente: exactamente este síntoma, reproducible solo cuando una clase con varios
 * registros corre antes de otra. Semilla aleatoria (en vez de una constante) hace la colisión
 * entre contextos distintos astronómicamente improbable sin tener que coordinar nada entre clases.</p>
 */
@Component
@Profile("test")
public class StaticTestUsuarioDirectorioPort implements UsuarioDirectorioPort {

    private final Map<String, Registro> porUsuario = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong(
            java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L));

    private record Registro(Long id, String clave, String correo, String nombres) {
    }

    @Override
    public Optional<UsuarioRemoto> autenticar(String usuario, String clave) {
        Registro registro = porUsuario.get(usuario);
        if (registro == null || !registro.clave().equals(clave)) {
            return Optional.empty();
        }
        return Optional.of(new UsuarioRemoto(registro.id(), usuario, registro.nombres(), null, registro.correo()));
    }

    /** Sin clave real todavia (ver {@code RegistroService}) — se guarda un valor desechable interno. */
    @Override
    public UsuarioRemoto crear(String usuario, String correo, String nombres) {
        if (porUsuario.containsKey(usuario)) {
            throw new EmailYaRegistradoException(correo);
        }
        Long id = secuencia.incrementAndGet();
        porUsuario.put(usuario, new Registro(id, "sin-clave-aun", correo, nombres));
        return new UsuarioRemoto(id, usuario, nombres, null, correo);
    }

    /**
     * Sin token real que resolver: en el flujo real, el token lo genera ms-usuario y nunca vuelve
     * a ms-xrello-cuentas (solo va en el correo) — esta clase no tiene cómo simular el "click en
     * el enlace" de punta a punta. <b>Por convención de este doble, {@code token == correo}</b>
     * (el mismo valor que devolvió {@link #crear} como {@code usuario}) — así cada test que
     * registra varios usuarios activa al que corresponde, nunca "cualquiera" (bug real en una
     * version anterior de esta clase: usaba {@code findFirst()} sin mirar el token, y devolvía
     * siempre el PRIMER usuario registrado en el contexto, sin importar a quien se queria
     * verificar).
     */
    @Override
    public UsuarioRemoto verificarCorreo(String token, String nuevaClave) {
        Registro registro = porUsuario.get(token);
        if (registro == null) {
            throw new com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException(
                    "Token de verificacion de correo invalido");
        }
        porUsuario.put(token, new Registro(registro.id(), nuevaClave, registro.correo(), registro.nombres()));
        return new UsuarioRemoto(registro.id(), token, registro.nombres(), null, registro.correo());
    }

    /** Nada que simular: el flujo real nunca vuelve a este doble, igual que {@link #verificarCorreo}. */
    @Override
    public void solicitarReseteoClave(String correo) {
        // no-op: ver Javadoc de la clase.
    }

    /** Mismo convenio {@code token == usuario} que {@link #verificarCorreo}. */
    @Override
    public UsuarioRemoto confirmarReseteoClave(String token, String nuevaClave) {
        Registro registro = porUsuario.get(token);
        if (registro == null) {
            throw new com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException(
                    "Token de reseteo de clave invalido");
        }
        porUsuario.put(token, new Registro(registro.id(), nuevaClave, registro.correo(), registro.nombres()));
        return new UsuarioRemoto(registro.id(), token, registro.nombres(), null, registro.correo());
    }
}
