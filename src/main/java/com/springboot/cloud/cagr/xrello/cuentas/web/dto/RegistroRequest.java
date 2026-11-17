package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sin {@code password}: la cuenta se crea sin clave usable y queda {@code activo=false} en
 * ms-usuario — la PRIMERA clave real se fija al verificar el correo (ver
 * {@code VerificarCorreoRequest}, {@code security.AuthService#verificarCorreo}), nunca antes.
 * {@code tokenInvitacion} es opcional — ver {@code security.RegistroService}.
 */
public record RegistroRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        String email,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        String tokenInvitacion) {
}
