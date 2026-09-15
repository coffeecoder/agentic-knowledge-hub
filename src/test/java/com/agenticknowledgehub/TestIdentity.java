package com.agenticknowledgehub;

import com.agenticknowledgehub.security.IdentityTokenValidator;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;

@TestConfiguration(proxyBeanMethods = false)
public class TestIdentity {
  static final String PROJECT = "agentic-knowledge-hub-learning";
  static final KeyPair KEY = key();

  static KeyPair key() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Bean
  @Primary
  JwtDecoder testIdentityDecoder() {
    var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
    decoder.setJwtValidator(IdentityTokenValidator.forProject(PROJECT));
    return decoder;
  }

  static Map<String, Object> permissions(String tenant, boolean publisher) {
    return Map.of(
        "tenant",
        tenant,
        "groups",
        List.of("support"),
        "permissions",
        publisher ? List.of("knowledge:read", "knowledge:write") : List.of("knowledge:read"),
        "write_sources",
        List.of("manual-text"),
        "assignable_groups",
        List.of("support"));
  }

  static JWTClaimsSet.Builder claims(String tenant) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer("https://securetoken.google.com/" + PROJECT)
        .audience(PROJECT)
        .subject("synthetic-user")
        .issueTime(Date.from(now.minusSeconds(5)))
        .expirationTime(Date.from(now.plusSeconds(600)))
        .claim("auth_time", now.minusSeconds(10).getEpochSecond())
        .claim("akh", permissions(tenant, true));
  }

  static String token(String tenant) {
    return sign(claims(tenant).build(), KEY);
  }

  static String sign(JWTClaimsSet claims, KeyPair key) {
    try {
      var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("synthetic-key").build(), claims);
      jwt.sign(new RSASSASigner(key.getPrivate()));
      return jwt.serialize();
    } catch (JOSEException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
