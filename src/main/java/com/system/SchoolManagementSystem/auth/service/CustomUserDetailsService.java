package com.system.SchoolManagementSystem.auth.service;

import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username/email: {}", username);

        // Try to find user by username or email
        User user = userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> {
                    log.warn("User not found with username/email: {}", username);
                    return new UsernameNotFoundException("User not found with username/email: " + username);
                });

        // Check if user is enabled
        if (!user.isEnabled()) {
            log.warn("User account is disabled: {}", username);
            throw new UsernameNotFoundException("User account is disabled");
        }

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            log.warn("User account is locked: {}", username);
            throw new UsernameNotFoundException("User account is locked");
        }

        log.debug("User loaded successfully: {}", username);
        return user;
    }

    @Transactional
    public User loadUserEntityByUsername(String username) {
        return userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}