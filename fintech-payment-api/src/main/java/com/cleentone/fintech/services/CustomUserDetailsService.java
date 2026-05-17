package com.cleentone.fintech.services;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * This class bridge the gap between our User database model and what 
 * Spring Security needs to perform authentication.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user from our database based on their email.
     * Spring Security calls this during the login process.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // We look for the user in our database. If they aren't there, we tell
        // Spring Security to bail out with a UsernameNotFoundException.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                    new UsernameNotFoundException("User not found: " + email)
                );
 
        // We map our user entity to Spring's UserDetails object.
        // For now, everyone gets the "ROLE_USER" authority.
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
