package com.dinoco.oficina.financeiro.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI financeiroOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Oficina — Financeiro").version("v1")
                        .description("Controle de pagamentos e estornos. Operações de criação e estorno são assíncronas."))
                .components(new Components().addSecuritySchemes("FinanceiroApiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")));
    }
}
