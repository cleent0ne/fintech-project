package com.cleentone.fintech.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cleentone.fintech.model.User;

/**
 * Standard repository for managing our User accounts.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>{
    
    /**
     * Finds a user by their email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if an email is already registered in our system.
     */
    boolean existsByEmail(String email);
}
