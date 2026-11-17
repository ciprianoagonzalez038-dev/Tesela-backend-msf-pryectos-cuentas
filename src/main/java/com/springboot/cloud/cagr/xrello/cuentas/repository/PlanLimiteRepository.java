package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.cloud.cagr.xrello.cuentas.domain.PlanLimite;

public interface PlanLimiteRepository extends JpaRepository<PlanLimite, Long> {

    /**
     * Valor de un limite segun el PLAN del usuario indicado — precedencia 2 en
     * {@code LimiteService} (despues del override de {@code cta_limite_usuario}).
     * JOIN directo contra {@code Usuario.plan} en una sola consulta, sin cargar
     * la entidad {@code Usuario} completa.
     */
    @Query("""
            SELECT pl FROM PlanLimite pl
            JOIN Usuario u ON u.plan = pl.plan
            WHERE u.id = :usuarioId AND pl.limite.codigo = :codigoLimite
            """)
    Optional<PlanLimite> findByUsuarioIdAndLimiteCodigo(
            @Param("usuarioId") Long usuarioId, @Param("codigoLimite") String codigoLimite);
}
