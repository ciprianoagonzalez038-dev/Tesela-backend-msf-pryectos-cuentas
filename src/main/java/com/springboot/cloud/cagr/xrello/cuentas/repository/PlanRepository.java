package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Plan;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCodigo(String codigo);
}
