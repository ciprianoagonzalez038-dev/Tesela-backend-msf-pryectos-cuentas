package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * {@code emailDestino} es obligatorio solo para {@code tipo = EMAIL_DIRIGIDO} (validado en el
 * servicio, no aqui — depende de otro campo del mismo request, ver
 * {@code invitacion.InvitacionService#crearInvitacion}). Para ese tipo, {@code usosMaximos} se
 * fuerza a 1 sin importar lo que llegue aqui.
 */
public record CrearInvitacionRequest(
        @NotBlank(message = "El tipo es obligatorio") String tipo,
        @Email(message = "Escribe un correo valido") String emailDestino,
        @NotBlank(message = "El rol asignado es obligatorio") String rolCodigo,
        @Positive(message = "Los dias de vigencia deben ser un numero positivo") long diasVigencia,
        @Positive(message = "Los usos maximos deben ser un numero positivo") int usosMaximos) {
}
