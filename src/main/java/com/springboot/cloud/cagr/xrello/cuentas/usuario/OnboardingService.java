package com.springboot.cloud.cagr.xrello.cuentas.usuario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.cloud.cagr.commons.exceptions.ResourceNotFoundException;
import com.springboot.cloud.cagr.xrello.cuentas.domain.Usuario;
import com.springboot.cloud.cagr.xrello.cuentas.repository.UsuarioRepository;

/** Versión del recorrido de primera vez que el usuario ya vio (ver {@code V11__onboarding_version_vista.sql}). */
@Service
public class OnboardingService {

    private final UsuarioRepository usuarioRepository;

    public OnboardingService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** {@code null} = nunca lo vio. */
    @Transactional(readOnly = true)
    public Integer versionVista(Long usuarioId) {
        return buscar(usuarioId).getOnboardingVersionVista();
    }

    /** Nunca baja la versión: marcar una anterior a la ya vista no hace nada. Devuelve la que queda. */
    @Transactional
    public Integer marcarVista(Long usuarioId, int version) {
        Usuario u = buscar(usuarioId);
        Integer actual = u.getOnboardingVersionVista();
        if (actual == null || version > actual) {
            u.setOnboardingVersionVista(version);
            usuarioRepository.save(u);
            return version;
        }
        return actual;
    }

    private Usuario buscar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
    }
}
