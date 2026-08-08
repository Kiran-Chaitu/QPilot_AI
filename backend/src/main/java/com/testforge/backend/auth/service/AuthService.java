package com.testforge.backend.auth.service;

import com.testforge.backend.auth.dto.AuthResponse;
import com.testforge.backend.auth.dto.LoginRequest;
import com.testforge.backend.auth.dto.RegisterRequest;
import com.testforge.backend.auth.dto.UserSummary;
import com.testforge.backend.auth.entity.Role;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.auth.repository.UserRepository;
import com.testforge.backend.auth.security.JwtService;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.UnauthorizedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Emails are always stored lowercase, so the uniqueness check must be too — otherwise
        // "User@x.com" registers a second row that can never be logged into (login lowercases).
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                request.role() != null ? request.role() : Role.DEVELOPER
        );
        userRepository.save(user);
        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, toSummary(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, toSummary(user));
    }

    private UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
