package com.springboot.cloud.cagr.xrello.cuentas.security.identidad;

import java.util.Optional;

import com.springboot.cloud.cagr.xrello.cuentas.exception.EmailYaRegistradoException;

/**
 * Puerto hacia el directorio centralizado de identidades (ms-usuario) — ver
 * {@link UsuarioDirectorioClient} (implementación real, HTTP) y el doble de prueba usado en los
 * tests de integración (perfil {@code test}, sin Eureka ni red real).
 */
public interface UsuarioDirectorioPort {

    /** Vacío si las credenciales son inválidas (nunca revela cuál de las dos falló). */
    Optional<UsuarioRemoto> autenticar(String usuario, String clave);

    /**
     * Da de alta la identidad canónica, SIN clave usable (ver Javadoc de
     * {@code security.RegistroService}) — queda {@code activo=false} hasta
     * {@link #verificarCorreo}. {@code usuario} y {@code correo} son el mismo valor (el correo
     * que escribió la persona).
     *
     * @throws EmailYaRegistradoException si el correo/usuario ya existe
     */
    UsuarioRemoto crear(String usuario, String correo, String nombres);

    /**
     * Consume el token del enlace de verificacion de correo (alta con {@code activo=false}, ver
     * {@link #crear}), fija {@code nuevaClave} como la PRIMERA clave real de la cuenta y la
     * activa en ms-usuario.
     *
     * @return la identidad ya activada, para iniciar sesion de inmediato
     * @throws com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException si
     *         el token no existe
     * @throws com.springboot.cloud.cagr.xrello.cuentas.exception.TokenVerificacionVencidoException
     *         si el token vencio
     */
    UsuarioRemoto verificarCorreo(String token, String nuevaClave);

    /**
     * Solicita el envio del correo de reseteo de clave. SIEMPRE "tiene exito" para el llamante
     * (nunca revela si el correo tiene cuenta, ver {@code AutenticacionServiceImpl} de ms-usuario).
     */
    void solicitarReseteoClave(String correo);

    /**
     * Consume el token del enlace de reseteo de clave y fija {@code nuevaClave}.
     *
     * @return la identidad con la clave ya actualizada, para iniciar sesion de inmediato
     * @throws com.springboot.cloud.cagr.xrello.cuentas.exception.RecursoNoEncontradoException si
     *         el token no existe
     * @throws com.springboot.cloud.cagr.xrello.cuentas.exception.TokenReseteoClaveVencidoException
     *         si el token vencio
     */
    UsuarioRemoto confirmarReseteoClave(String token, String nuevaClave);
}
