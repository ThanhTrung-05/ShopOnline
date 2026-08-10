package com.example.banhangtructuyen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Keycloak Admin REST API client used to provision customer
 * identities on registration. All values come from environment/application properties —
 * none are hardcoded (see {@code app.keycloak.admin.*}).
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.keycloak.admin")
public class KeycloakAdminProperties {

    private String baseUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private String backendClientId = "shoponline-backend";
    private String customerRole = "CUSTOMER";
}
