package com.cleentone.fintech.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a user in our system. We keep it simple: just an email, 
 * a secure password hash, and their full name.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Email must be unique and is used for logging in.
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // We never store plain passwords, only their secure hashes.
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    // Automatically tracked when the user record is first created.
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}