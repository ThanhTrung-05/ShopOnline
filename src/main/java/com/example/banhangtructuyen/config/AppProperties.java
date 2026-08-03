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
}
