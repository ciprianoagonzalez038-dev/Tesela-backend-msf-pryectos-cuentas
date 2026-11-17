package com.springboot.cloud.cagr.xrello.cuentas.invitacion;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Invitacion;

/**
 * Resultado de {@code InvitacionService#crearInvitacion}: la entidad creada
 * junto con el token EN CLARO — la unica vez que existe fuera de la memoria
 * del proceso que lo genero. Nunca loguear {@code tokenEnClaro}.
 */
public record InvitacionCreada(Invitacion invitacion, String tokenEnClaro) {
}
