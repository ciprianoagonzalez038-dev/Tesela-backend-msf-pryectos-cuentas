package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.cloud.cagr.xrello.cuentas.domain.RolSistema;

public interface RolSistemaRepository extends JpaRepository<RolSistema, Long> {

    Optional<RolSistema> findByCodigo(String codigo);
}
