package com.springboot.cloud.cagr.xrello.cuentas.exception;

import lombok.Getter;

/**
 * Se intento superar un limite de cupo resuelto (ver
 * {@code limite.LimiteService}). Mapeada a 422 por
 * {@link CuentasExceptionHandler}: la peticion es valida, pero la operacion
 * no puede procesarse por una regla de negocio (cupo agotado).
 */
@Getter
public class LimiteExcedidoException extends CuentasException {

    private final String codigoLimite;
    private final Integer valorMaximo;
    private final long usoActual;

    public LimiteExcedidoException(String codigoLimite, Integer valorMaximo, long usoActual) {
        super("Limite '" + codigoLimite + "' excedido: uso actual " + usoActual
                + ", maximo permitido " + valorMaximo);
        this.codigoLimite = codigoLimite;
        this.valorMaximo = valorMaximo;
        this.usoActual = usoActual;
    }
}
