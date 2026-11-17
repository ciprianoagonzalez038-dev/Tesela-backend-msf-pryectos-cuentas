package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springboot.cloud.cagr.xrello.cuentas.domain.UsuarioRolSistema;

public interface UsuarioRolSistemaRepository extends JpaRepository<UsuarioRolSistema, Long> {

    /**
     * Codigos de los roles de sistema del usuario, en una sola consulta — usado por
     * {@code security.TokenService} para poblar el claim {@code roles} del JWT sin cargar las
     * entidades completas.
     */
    @Query("SELECT u.rolSistema.codigo FROM UsuarioRolSistema u WHERE u.usuario.id = :usuarioId")
    List<String> findCodigosRolByUsuarioId(@Param("usuarioId") Long usuarioId);
}
