package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearProyectoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 160, message = "El nombre no puede superar los 160 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripcion no puede superar los 500 caracteres")
        String descripcion,

        @NotBlank(message = "El prefijo es obligatorio")
        @Size(max = 10, message = "El prefijo no puede superar los 10 caracteres")
        String prefijo,

        /**
         * Código de plantilla de ms-xrello-tableros (DESARROLLO/SIMPLE) — opcional, sin
         * validación aquí: el catálogo real vive en el otro microservicio (ver
         * V10__proyecto_plantilla.sql). {@code null}/vacío = usar la predeterminada.
         */
        String plantillaCodigo) {
}
