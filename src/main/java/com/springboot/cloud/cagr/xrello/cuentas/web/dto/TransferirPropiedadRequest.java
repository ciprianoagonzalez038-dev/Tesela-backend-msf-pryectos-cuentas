package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Identifica al nuevo owner por email (no tiene {@code codigo} publico propio como
 * proyecto/invitacion) — nunca por su id numerico interno.
 */
public record TransferirPropiedadRequest(
        @NotBlank(message = "El email del nuevo owner es obligatorio")
        @Email(message = "El email no tiene un formato valido")
        String emailNuevoOwner) {
}
