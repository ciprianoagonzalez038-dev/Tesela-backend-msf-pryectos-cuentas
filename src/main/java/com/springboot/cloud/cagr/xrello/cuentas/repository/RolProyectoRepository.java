package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.cloud.cagr.xrello.cuentas.domain.RolProyecto;

public interface RolProyectoRepository extends JpaRepository<RolProyecto, Long> {

    Optional<RolProyecto> findByCodigo(String codigo);
}
