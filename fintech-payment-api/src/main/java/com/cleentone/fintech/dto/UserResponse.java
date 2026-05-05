package com.cleentone.fintech.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cleentone.fintech.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

@Getter
public class UserResponse {
    
   private UUID id;
    private String email;
 
    @JsonProperty("full_name")
    private String fullName;
 
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.email = user.getEmail();
        response.fullName = user.getFullName();
        response.createdAt = user.getCreatedAt();
        return response;
    }

}
