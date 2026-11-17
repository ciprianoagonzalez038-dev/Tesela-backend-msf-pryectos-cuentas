package com.springboot.cloud.cagr.xrello.cuentas.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRolSistemaRepository;

// TRANSITORIO: migra al Authorization Server — la EMISION propia de access tokens desaparece
// (el Authorization Server la asume); ver XRELLO_CONTRATO.md, seccion "MIGRACION A OAUTH2". La
// CONVENCION de claims que este servicio establece (roles de SISTEMA si, roles de PROYECTO
// nunca) no es transitoria — sigue aplicando a cualquier token que emita el Authorization Server,
// y es lo que security.SecurityConfig#jwtAuthenticationConverter sigue leyendo sin importar quien
// firme el token.
/**
 * Emite access tokens JWT. {@code sub} = id del usuario, mas {@code email},
 * {@code nombre} y {@code roles} (roles de SISTEMA del usuario, ver
 * {@code domain.RolSistema}) como claims planos — DELIBERADAMENTE sin roles de
 * proyecto (dependen del proyecto consultado y cambian; ver
 * {@code security.AccesoProyecto}, que resuelve el rol en cada request contra
 * la base, nunca desde el token).
 */
@Service
public class TokenService {

    /** Claim {@code iss} de los tokens PROPIOS; lo usa tambien el decoder local (ver EmisoresConfig). */
    public static final String EMISOR = "ms-xrello-cuentas";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final UsuarioRolSistemaRepository usuarioRolSistemaRepository;

    public TokenService(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            UsuarioRolSistemaRepository usuarioRolSistemaRepository) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.usuarioRolSistemaRepository = usuarioRolSistemaRepository;
    }

    public String generarAccessToken(Usuario usuario) {
        Instant ahora = Instant.now();
        List<String> rolesSistema = usuarioRolSistemaRepository.findCodigosRolByUsuarioId(usuario.getId());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(jwtProperties.getAccessTokenMinutes(), ChronoUnit.MINUTES))
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getEmail())
                .claim("nombre", usuario.getNombre())
                .claim("roles", rolesSistema)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
