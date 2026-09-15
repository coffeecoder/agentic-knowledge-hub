package com.agenticknowledgehub.config;

import com.agenticknowledgehub.security.*;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
  @Bean
  JwtDecoder identityDecoder(@Value("${akh.identity.project-id}") String project) {
    var decoder =
        NimbusJwtDecoder.withJwkSetUri(
                "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(IdentityTokenValidator.forProject(project));
    return decoder;
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, Environment environment, JwtDecoder decoder)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(HttpMethod.GET, "/health/live").permitAll();
              if (environment.acceptsProfiles(Profiles.of("dev"))) {
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/docs",
                        "/swagger-ui/**",
                        "/openapi.json",
                        "/openapi.json/**")
                    .permitAll();
              }
              auth.requestMatchers(HttpMethod.POST, "/v1/search")
                  .hasAuthority("knowledge:read")
                  .requestMatchers(HttpMethod.POST, "/v1/documents/text")
                  .hasAuthority("knowledge:write")
                  .anyRequest()
                  .denyAll();
            })
        .oauth2ResourceServer(
            resource ->
                resource
                    .jwt(
                        jwt ->
                            jwt.decoder(decoder)
                                .jwtAuthenticationConverter(
                                    token -> {
                                      List<SimpleGrantedAuthority> authorities;
                                      try {
                                        authorities =
                                            IdentityClaims.from(token).permissions().stream()
                                                .filter(
                                                    permission ->
                                                        permission.equals("knowledge:read")
                                                            || permission.equals("knowledge:write"))
                                                .map(SimpleGrantedAuthority::new)
                                                .toList();
                                      } catch (AccessDeniedException denied) {
                                        authorities = List.of();
                                      }
                                      return new JwtAuthenticationToken(
                                          token, authorities, token.getSubject());
                                    }))
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(401);
                          response.setHeader("WWW-Authenticate", "Bearer");
                          response.setContentType("application/json");
                          response.getWriter().write("{\"detail\":\"Authentication required\"}");
                        }))
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    (request, response, exception) -> {
                      response.setStatus(403);
                      response.setContentType("application/json");
                      response.getWriter().write("{\"detail\":\"Application access denied\"}");
                    }));
    return http.build();
  }

  @Bean
  CallerContextProvider callerContextProvider() {
    return () -> {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
        throw new SearchAccessDeniedException();
      }
      var claims = IdentityClaims.from(token.getToken());
      return new CallerContext(token.getName(), claims.tenant(), claims.groups());
    };
  }

  @Bean
  OpenAPI authenticatedOpenApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "identityToken",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("identityToken"));
  }
}
