package com.cleentone.fintech.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * This class configures Swagger (OpenAPI) for our project. 
 * It sets the title and description for the documentation UI and tells 
 * Swagger how to handle our JWT-based authentication.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Fintech Payment API",
        version = "1.0",
        description = "A secure, high-performance API for managing multi-currency wallets and P2P transfers."
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
public class OpenApiConfig {
    // This is a configuration-only class, no logic needed here!
}
