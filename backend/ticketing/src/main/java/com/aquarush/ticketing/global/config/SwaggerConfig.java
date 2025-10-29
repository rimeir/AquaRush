package com.aquarush.ticketing.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(customInfo())
                .addSecurityItem(customSecurityRequirement())
                .components(customComponents());
    }

    private Info customInfo() {
        return new Info()
                .title("AquaRush API Document")
                .version("1.0")
                .description("AquaRush API 문서");
    }

    String authName = "Json Web Token";

    private SecurityRequirement customSecurityRequirement() {
        return new SecurityRequirement().addList(authName);
    }

    private Components customComponents() {
        return new Components().addSecuritySchemes(authName,
                new SecurityScheme()
                        .name(authName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("Bearer")
                        .bearerFormat("JWT")
                        .description("Access Token을 입력해주세요.(Bearer 제외)")
        );
    }

}