package com.auction.app;

import com.auction.controller.SwaggerUiController;
import com.auction.controller.user.UserController;
import io.swagger.v3.jaxrs2.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.glassfish.jersey.server.ResourceConfig;

import java.util.Set;

public class AppConfig extends ResourceConfig {

    public AppConfig() {
        register(UserController.class);
        register(SwaggerUiController.class);
        register(OpenApiResource.class);
        register(SwaggerSerializers.class);

        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("Online Auction System API")
                        .version("1.0")
                        .description("REST API for the Online Auction System"));

        SwaggerConfiguration config = new SwaggerConfiguration()
                .openAPI(openApi)
                .prettyPrint(true)
                .resourcePackages(Set.of("com.auction.controller.user"));

        try {
            new JaxrsOpenApiContextBuilder<>()
                    .openApiConfiguration(config)
                    .buildContext(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Swagger", e);
        }
    }
}
