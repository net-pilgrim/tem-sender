package com.scaleway.mailsender;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(
                title = "Scaleway TEM Multitenant Mail Sending Microservice",
                version = "1.0",
                description = "Reliable multitenant mail sending microservice built with Java & Micronaut, using Scaleway Transactional Email (TEM) with automated SPF/DKIM verification and persistent outbox queue.",
                contact = @Contact(name = "Dev Team")
        ),
        security = {
                @SecurityRequirement(name = "ApiKeyAuth"),
                @SecurityRequirement(name = "MasterKeyAuth")
        }
)
@SecurityScheme(
        name = "ApiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "Tenant API Key (sk_live_...) for /api/v1/** endpoints"
)
@SecurityScheme(
        name = "MasterKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Admin-Key",
        description = "Master Admin Key for /admin/** endpoints"
)
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
