package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.cloud.cagr.xrello.cuentas.domain.RefreshToken;

// TRANSITORIO: migra al Authorization Server — desaparece junto con la tabla cta_refresh_token
// y la entidad RefreshToken; ver XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2".
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Deteccion de reuso: un refresh token ya revocado que se vuelve a usar indica que fue
     * robado (o hay una carrera de doble-uso) — revoca TODAS las sesiones del usuario, no solo
     * la reutilizada.
     *
     * <p>Sin {@code @Transactional(REQUIRES_NEW)} aqui a proposito: vivia en este metodo, pero
     * se movio a {@code security.RefreshTokenRevocacionService#revocarTodasLasSesiones}, que es
     * quien ahora abre esa transaccion independiente ANTES de llamar a este metodo — ver el
     * Javadoc de esa clase para la razon completa (deteccion de reuso + rollback de la
     * transaccion llamadora).</p>
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revocado = true WHERE r.usuario.id = :usuarioId AND r.revocado = false")
    int revocarTodosDelUsuario(@Param("usuarioId") Long usuarioId);
}
