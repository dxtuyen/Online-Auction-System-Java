package com.auction.controller.user;

import com.auction.dto.user.UserDto;
import com.auction.dto.user.UserLoginRequestDto;
import com.auction.dto.user.UserRegisterRequestDto;
import com.auction.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users")
@Tag(name = "User Management", description = "Endpoints for user operations")
public class UserController {
    private final UserService userService = new UserService();

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register a new user",
               description = "Creates a new user with the provided username and password",
               responses = {
                   @ApiResponse(responseCode = "200", description = "User registered successfully",
                                content = @Content(schema = @Schema(implementation = UserDto.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid input or username already exists")
               })
    public Response register(UserRegisterRequestDto registrationRequest) {
        try {
            UserDto userDto = userService.register(registrationRequest);
            return Response.ok(userDto).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(e.getMessage())
                           .build();
        }
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Login to account",
            description = "Login into account",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User login successfully",
                            content = @Content(schema = @Schema(implementation = UserDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid password or username")
            })
    public Response login(UserLoginRequestDto loginRequest) {
        try {
            boolean result = userService.login(loginRequest);
            if (!result) throw new RuntimeException("Invalid password or username");
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
