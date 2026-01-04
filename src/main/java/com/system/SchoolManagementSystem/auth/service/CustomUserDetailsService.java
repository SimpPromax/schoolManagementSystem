package com.system.SchoolManagementSystem.auth.service;

import com.system.SchoolManagementSystem.auth.entity.User;
import com.system.SchoolManagementSystem.auth.repository.UserRepository;
import com.system.SchoolManagementSystem.config.TenantContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String tenantId = TenantContext.getCurrentTenant();

        User user;

        if ("master".equals(tenantId)) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        } else {
            user = userRepository.findByUsernameAndTenantId(username, tenantId)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User not found with username: " + username + " and tenant: " + tenantId));
        }

        return user;
    }
}