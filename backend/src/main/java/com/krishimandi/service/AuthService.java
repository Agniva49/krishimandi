package com.krishimandi.service;

import com.krishimandi.model.User;
import com.krishimandi.repository.UserRepository;
import com.krishimandi.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, AuthenticationManager authenticationManager,
                       UserDetailsServiceImpl userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    public Map<String, Object> register(String name, String email,
                                         String password, String state, String language) {
        if (userRepository.existsByEmail(email))
            throw new IllegalArgumentException("Email already registered");

        User user = User.builder()
            .name(name).email(email)
            .password(passwordEncoder.encode(password))
            .state(state).language(language)
            .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {}", email);

        UserDetails ud = userDetailsService.loadUserByUsername(email);
        String token = jwtUtil.generateToken(ud, saved.getId(), saved.getRole());
        return buildAuthResponse(saved, token);
    }

    public Map<String, Object> login(String email, String password) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password));

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserDetails ud = userDetailsService.loadUserByUsername(email);
        String token = jwtUtil.generateToken(ud, user.getId(), user.getRole());
        log.info("User logged in: {}", email);
        return buildAuthResponse(user, token);
    }

    public Map<String, Object> getProfile(String email) {
        User u = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Map<String, Object> profile = new HashMap<>();
        profile.put("id",               u.getId());
        profile.put("name",             u.getName());
        profile.put("email",            u.getEmail());
        profile.put("role",             u.getRole());
        profile.put("state",            Objects.toString(u.getState(), ""));
        profile.put("language",         u.getLanguage());
        profile.put("alertsEnabled",    u.isAlertsEnabled());
        profile.put("totalPredictions", u.getTotalPredictions());
        profile.put("createdAt",        u.getCreatedAt());
        return profile;
    }

    public Map<String, Object> updateProfile(String email, Map<String, Object> updates) {
        User u = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (updates.containsKey("name"))          u.setName((String) updates.get("name"));
        if (updates.containsKey("state"))         u.setState((String) updates.get("state"));
        if (updates.containsKey("language"))      u.setLanguage((String) updates.get("language"));
        if (updates.containsKey("alertsEnabled")) u.setAlertsEnabled((Boolean) updates.get("alertsEnabled"));
        userRepository.save(u);
        return getProfile(email);
    }

    private Map<String, Object> buildAuthResponse(User user, String token) {
        Map<String, Object> r = new HashMap<>();
        r.put("token",  token);
        r.put("type",   "Bearer");
        r.put("userId", user.getId());
        r.put("name",   user.getName());
        r.put("email",  user.getEmail());
        r.put("role",   user.getRole());
        return r;
    }
}
