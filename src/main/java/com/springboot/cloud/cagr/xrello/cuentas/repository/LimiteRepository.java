package com.springboot.cloud.cagr.xrello.cuentas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Limite;

public interface LimiteRepository extends JpaRepository<Limite, Long> {
}
