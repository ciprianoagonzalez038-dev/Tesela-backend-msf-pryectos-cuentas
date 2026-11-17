package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.cloud.cagr.xrello.cuentas.domain.LimiteUsuario;

public interface LimiteUsuarioRepository extends JpaRepository<LimiteUsuario, Long> {

    /** Override puntual de un limite para un usuario — precedencia 1 en {@code LimiteService}. */
    Optional<LimiteUsuario> findByUsuario_IdAndLimite_Codigo(Long usuarioId, String codigoLimite);
}
