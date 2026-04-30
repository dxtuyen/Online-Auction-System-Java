package com.auction.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/swagger-ui")
public class SwaggerUiController {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getSwaggerUi() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>Swagger UI</title>
              <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css" >
              <style>
                html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
                *, *:before, *:after { box-sizing: inherit; }
                body { margin:0; background: #fafafa; }
              </style>
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js"> </script>
              <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js"> </script>
              <script>
              window.onload = function() {
                const ui = SwaggerUIBundle({
                  url: "/openapi.json",
                  dom_id: '#swagger-ui',
                  deepLinking: true,
                  presets: [
                    SwaggerUIBundle.presets.apis,
                    SwaggerUIStandalonePreset
                  ],
                  plugins: [
                    SwaggerUIBundle.plugins.DownloadUrl
                  ],
                  layout: "StandaloneLayout"
                })
                window.ui = ui
              }
              </script>
            </body>
            </html>
            """;
    }
}
