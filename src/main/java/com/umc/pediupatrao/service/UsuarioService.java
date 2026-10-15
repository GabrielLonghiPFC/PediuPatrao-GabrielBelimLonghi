package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.ControlePerfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class UsuarioService {

    private static final Set<String> PERFIS = Set.of("ADMIN", "GERENTE", "ATENDENTE");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ControlePerfil controlePerfil;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, ControlePerfil controlePerfil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.controlePerfil = controlePerfil;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Salva um novo usuário (criptografando a senha)
    public void salvarUsuario(Usuario usuario) {
        controlePerfil.verificar("ADMIN");
        validarPerfil(usuario.getRole());
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
    }

    public void deletarUsuario(String id) {
        controlePerfil.verificar("ADMIN");
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        if (usuario.getUsername().equals(controlePerfil.usuarioLogado())) {
            throw new IllegalArgumentException("Você não pode excluir o próprio usuário.");
        }
        usuarioRepository.deleteById(id);
    }

    public Iterable<Usuario> listarTodos() {
        controlePerfil.verificar("ADMIN");
        return usuarioRepository.findAll();
    }

    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        controlePerfil.verificar("ADMIN");
        usuarioRepository.findById(id).ifPresent(usuarioExistente -> {
            boolean proprio = usuarioExistente.getUsername().equals(controlePerfil.usuarioLogado());
            usuarioExistente.setUsername(usuarioAtualizado.getUsername());
            if (!proprio) {
                validarPerfil(usuarioAtualizado.getRole());
                usuarioExistente.setRole(usuarioAtualizado.getRole());
            }

            if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
                usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
            }

            usuarioRepository.save(usuarioExistente);
        });
    }

    private void validarPerfil(String role) {
        if (role == null || !PERFIS.contains(role)) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
    }
}
