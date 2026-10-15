package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo) {
        return args -> {

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            repo.findByUsername("admin").ifPresentOrElse(admin -> {
                if (admin.getRole() == null || admin.getRole().isBlank()) {
                    admin.setRole("ADMIN");
                    repo.save(admin);
                }
            }, () -> {
                Usuario user = new Usuario();
                user.setUsername("admin");
                user.setPassword(encoder.encode("teste123"));
                user.setRole("ADMIN");
                repo.save(user);
            });

        };
    }
}