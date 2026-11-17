package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.xrello.cuentas.domain.RefreshToken;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.exception.CredencialesInvalidasException;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.AprovisionamientoUsuarioService;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.IdentidadUsuario;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.UsuarioDirectorioPort;
import com.springboot.cloud.cagr.xrello.cuentas.security.identidad.UsuarioRemoto;

// Login local con password propio (BCrypt) RETIRADO (D28/Tesela): las credenciales se verifican
// contra ms-usuario (ver UsuarioDirectorioClient), nunca contra password_hash local. Ver
// XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2" (el camino elegido fue proxy a ms-usuario desde
// este mismo backend, no un redirect OAuth2 Authorization Code desde app-xrello-web).
/**
 * Login y refresh — orquesta {@link TokenService} (access) y
 * {@link RefreshTokenService} (refresh).
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioDirectorioPort directorioClient;
    private final AprovisionamientoUsuarioService aprovisionamientoUsuarioService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            UsuarioDirectorioPort directorioClient,
            AprovisionamientoUsuarioService aprovisionamientoUsuarioService,
            TokenService tokenService,
            RefreshTokenService refreshTokenService) {
        this.usuarioRepository = usuarioRepository;
        this.directorioClient = directorioClient;
        this.aprovisionamientoUsuarioService = aprovisionamientoUsuarioService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Verifica las credenciales contra ms-usuario (nunca contra una tabla local) y, si son
     * validas, resuelve/crea la fila LOCAL de {@code cta_usuario} enlazada por
     * {@code subject_externo} (mismo aprovisionamiento just-in-time que ya usa
     * {@code ResolutorIdentidad} para un JWT externo — aqui se alimenta con el perfil que
     * devolvio ms-usuario en vez de con claims de un token).
     *
     * <p>Mensaje SIEMPRE identico si el email no existe o si la clave no coincide — ms-usuario ya
     * no distingue las dos causas en su propia respuesta (ver {@code AutenticacionServiceImpl}),
     * asi que no hay nada que normalizar aqui.</p>
     *
     * <p>Sin {@code @Transactional} a proposito: {@link AprovisionamientoUsuarioService} maneja su
     * propia transaccion (debe poder CERRARLA del todo para releer tras un
     * {@code DataIntegrityViolationException} concurrente — ver su Javadoc) y
     * {@link RefreshTokenService#generarYAlmacenar} ya tiene la suya.</p>
     */
    public TokenPair login(String email, String rawPassword) {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        UsuarioRemoto remoto = directorioClient.autenticar(correo, rawPassword)
                .orElseThrow(() -> new CredencialesInvalidasException("Email o password incorrectos"));

        IdentidadUsuario identidad = aprovisionamientoUsuarioService.resolverOAprovisionar(
                String.valueOf(remoto.id()), remoto.correo(), remoto.nombres(), false);
        Usuario usuario = usuarioRepository.findById(identidad.usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario local recien resuelto/creado no encontrado: id=" + identidad.usuarioId()));
        return emitirPar(usuario);
    }

    /** Rota el refresh token (ver {@link RefreshTokenService#rotar}) y emite un par nuevo. */
    @Transactional
    public TokenPair refrescar(String refreshTokenEnClaro) {
        RefreshToken rotado = refreshTokenService.rotar(refreshTokenEnClaro);
        return emitirPar(rotado.getUsuario());
    }

    /**
     * Consume el token de verificacion de correo (enlace del correo de alta), fija
     * {@code nuevaClave} como la PRIMERA clave real de la cuenta en ms-usuario (unico dueño del
     * estado de verificacion/{@code activo}/clave) y, con la identidad ya activada, aprovisiona/
     * resuelve la fila local e inicia sesion de inmediato — mismo patron que {@link #login}.
     */
    public TokenPair verificarCorreo(String token, String nuevaClave) {
        UsuarioRemoto remoto = directorioClient.verificarCorreo(token, nuevaClave);

        IdentidadUsuario identidad = aprovisionamientoUsuarioService.resolverOAprovisionar(
                String.valueOf(remoto.id()), remoto.correo(), remoto.nombres(), false);
        Usuario usuario = usuarioRepository.findById(identidad.usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario local recien resuelto/creado no encontrado: id=" + identidad.usuarioId()));
        return emitirPar(usuario);
    }

    /** Siempre "tiene exito" (ver {@link UsuarioDirectorioPort#solicitarReseteoClave}). */
    public void solicitarReseteoClave(String email) {
        directorioClient.solicitarReseteoClave(email.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Consume el token del enlace de reseteo de clave, fija {@code nuevaClave} en ms-usuario y,
     * con la identidad ya actualizada, aprovisiona/resuelve la fila local e inicia sesion de
     * inmediato — mismo patron que {@link #verificarCorreo}.
     */
    public TokenPair confirmarReseteoClave(String token, String nuevaClave) {
        UsuarioRemoto remoto = directorioClient.confirmarReseteoClave(token, nuevaClave);

        IdentidadUsuario identidad = aprovisionamientoUsuarioService.resolverOAprovisionar(
                String.valueOf(remoto.id()), remoto.correo(), remoto.nombres(), false);
        Usuario usuario = usuarioRepository.findById(identidad.usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario local recien resuelto/creado no encontrado: id=" + identidad.usuarioId()));
        return emitirPar(usuario);
    }

    private TokenPair emitirPar(Usuario usuario) {
        String accessToken = tokenService.generarAccessToken(usuario);
        String refreshToken = refreshTokenService.generarYAlmacenar(usuario);
        return new TokenPair(accessToken, refreshToken);
    }
}
