package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/** Propiedades bajo {@code app.security.aprovisionamiento}. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.aprovisionamiento")
public class AprovisionamientoProperties {

    /**
     * Permite vincular, por correo, un token externo a una cuenta local EXISTENTE aunque el emisor
     * no afirme {@code email_verified=true}. <b>Apagado por defecto, y debe seguir apagado mientras
     * los correos no se verifiquen y el registro del ecosistema sea abierto</b>: con el correo sin
     * verificar, quien registre el correo de otra persona en el servicio de usuarios heredaria su
     * cuenta de Xrello (proyectos, membresias). Solo se enciende de forma temporal y consciente
     * durante una migracion controlada de usuarios (ver PLAN_MIGRACION_USUARIOS.md).
     */
    private boolean vincularPorEmailSinVerificar = false;
}
