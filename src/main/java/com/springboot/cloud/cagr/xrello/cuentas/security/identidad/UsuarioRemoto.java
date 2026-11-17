package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Identidad devuelta por ms-usuario (autenticar o crear). Proyección mínima del DTO de
 * ms-usuario; un campo nuevo allá no debe romper la deserialización aquí.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioRemoto(Long id, String usuario, String nombres, String apellidos, String correo) {
}
