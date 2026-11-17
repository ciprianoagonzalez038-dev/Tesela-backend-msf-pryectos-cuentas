package com.springboot.cloud.cagr.xrello.cuentas.web.dto;

/**
 * {@code usuarioId}: agregado para que ms-xrello-tableros/app-xrello-web puedan resolver
 * {@code asignados} (una lista de ids planos, sin nombre — tableros no tiene acceso a
 * {@code cta_usuario}) contra esta misma lista de miembros, sin una llamada adicional por
 * tarjeta/asignado. Cambio ADITIVO — ningún campo existente cambia de nombre ni de tipo. Protegido
 * por el mismo {@code @PreAuthorize} que el resto de la respuesta (ver
 * {@code MiembroController#listar}): quien ya ve el nombre/email de un miembro en esta lista
 * también puede ver su id.
 */
public record MiembroResponse(Long usuarioId, String email, String nombre, String rolCodigo, boolean esOwner) {
}
