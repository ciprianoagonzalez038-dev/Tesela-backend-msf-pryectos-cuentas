package com.springboot.cloud.cagr.xrello.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    /** Resuelve la cuenta local por el {@code sub} del Authorization Server (ver security.identidad). */
    Optional<Usuario> findBySubjectExterno(String subjectExterno);
}
