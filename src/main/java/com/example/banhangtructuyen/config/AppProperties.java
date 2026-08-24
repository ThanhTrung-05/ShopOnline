package com.example.banhangtructuyen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Redis redis = new Redis();
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Redis {
        private Ttl ttl = new Ttl();
    }

    @Getter
    @Setter
    public static class Ttl {
        private int productList = 300;
        private int productDetail = 600;
    }

    @Getter
    @Setter
    public static class Jwt {
        /** HMAC signing secret — must be at least 32 bytes for HS256. Override in each environment. */
        private String secret = "change-me-local-dev-secret-key-please-override-in-prod-32b+";
        /** Access-token lifetime in milliseconds (default 24h). */
        private long expirationMs = 86_400_000L;
    }
}
