package com.umc.pediupatrao.service;

import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    String role = usuario.getRole();
                    if (role == null || role.isBlank()) {
                        throw new UsernameNotFoundException("Usuário sem perfil definido");
                    }
                    return User.builder()
                            .username(usuario.getUsername())
                            .password(usuario.getPassword())
                            .roles(role)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
