package com.testforge.backend.config;

import com.testforge.backend.auth.entity.Role;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUserIfAbsent("dev@testforge.com", "Dev Tester", "password123", Role.DEVELOPER);
        seedUserIfAbsent("qa@testforge.com", "QA Lead Tester", "password123", Role.QA_LEAD);
        seedUserIfAbsent("admin@testforge.com", "System Admin", "password123", Role.ADMIN);
    }

    private void seedUserIfAbsent(String email, String name, String password, Role role) {
        if (!userRepository.existsByEmail(email.toLowerCase())) {
            User user = new User(
                    name,
                    email.toLowerCase(),
                    passwordEncoder.encode(password),
                    role
            );
            userRepository.save(user);
            log.info("Seeded demo user: {} ({})", email, role);
        }
    }
}
