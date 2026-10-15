package com.umc.pediupatrao.config;

import java.util.Arrays;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ControlePerfil {

    public void verificar(String... perfis) {
        Authentication auth = autenticacao();
        boolean ok = Arrays.stream(perfis)
                .anyMatch(perfil -> auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + perfil)));
        if (!ok) {
            throw new AccessDeniedException("Sem permissão.");
        }
    }

    public String usuarioLogado() {
        return autenticacao().getName();
    }

    private Authentication autenticacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Sem permissão.");
        }
        return auth;
    }
}