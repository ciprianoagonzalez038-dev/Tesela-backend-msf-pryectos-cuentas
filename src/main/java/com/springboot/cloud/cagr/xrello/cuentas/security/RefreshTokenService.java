package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.xrello.cuentas.domain.RefreshToken;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CredencialesInvalidasException;
import com.springboot.cloud.cagr.xrello.cuentas.exception.ReusoRefreshTokenException;
import com.springboot.cloud.cagr.xrello.cuentas.repository.RefreshTokenRepository;

// TRANSITORIO: migra al Authorization Server — junto con el resto del paquete de refresh
// tokens; ver XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2".
/**
 * Emite, rota y detecta el reuso de refresh tokens opacos. El token en claro
 * nunca se persiste ni se loguea — solo su SHA-256 ({@link TokenHasher}).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRevocacionService refreshTokenRevocacionService;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties,
            RefreshTokenRevocacionService refreshTokenRevocacionService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRevocacionService = refreshTokenRevocacionService;
    }

    /** Genera un refresh token nuevo para el usuario y devuelve el valor EN CLARO (una sola vez). */
    @Transactional
    public String generarYAlmacenar(Usuario usuario) {
        String tokenEnClaro = TokenHasher.generarTokenAleatorio();

        RefreshToken entidad = new RefreshToken();
        entidad.setTokenHash(TokenHasher.sha256Hex(tokenEnClaro));
        entidad.setUsuario(usuario);
        entidad.setFechaExpiracion(LocalDateTime.now().plusDays(jwtProperties.getRefreshTokenDays()));
        entidad.setRevocado(false);
        refreshTokenRepository.save(entidad);

        return tokenEnClaro;
    }

    /**
     * Valida y ROTA un refresh token: lo marca revocado (el llamador debe emitir uno nuevo).
     *
     * <p><b>Deteccion de reuso</b>: si el token ya estaba revocado (ya se habia usado antes
     * para rotar), significa que circulan dos copias del mismo refresh token — robo o carrera.
     * En ese caso se revocan TODOS los refresh tokens del usuario (no solo este) y se lanza
     * {@link ReusoRefreshTokenException}: cualquier sesion activa de ese usuario queda invalidada,
     * forzando un login nuevo.</p>
     *
     * @return la entidad (ya marcada revocada) con el {@link Usuario} asociado, para que el
     *         llamador emita el access token y el refresh token nuevos.
     */
    @Transactional
    public RefreshToken rotar(String tokenEnClaro) {
        String hash = TokenHasher.sha256Hex(tokenEnClaro);
        RefreshToken existente = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new CredencialesInvalidasException("Refresh token invalido"));

        if (Boolean.TRUE.equals(existente.getRevocado())) {
            refreshTokenRevocacionService.revocarTodasLasSesiones(existente.getUsuario().getId());
            throw new ReusoRefreshTokenException(
                    "Reuso de refresh token detectado; todas las sesiones del usuario fueron revocadas");
        }
        if (existente.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new CredencialesInvalidasException("Refresh token expirado");
        }

        existente.setRevocado(true);
        return existente;
    }
}
