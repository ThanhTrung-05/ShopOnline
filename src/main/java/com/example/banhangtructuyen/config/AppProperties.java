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
        private int featuredProducts = 300;
    }

    private Inventory inventory = new Inventory();

    @Getter
    @Setter
    public static class Inventory {
        /** ATS-18: products with availableQty <= this value are flagged as LOW_STOCK. Default 10. */
        private int lowStockThreshold = 10;
    }

    /**
     * ATS-27/ATS-28: Payment and QR configuration.
     */
    private Payment payment = new Payment();

    @Getter
    @Setter
    public static class Payment {
        /**
         * ATS-28: Enables the simulated payment confirmation endpoint (POST /api/v1/payments/demo/confirm).
         * MUST be false in production. Set to true only in local/dev/demo environments.
         */
        private boolean demoMode = false;

        /**
         * ATS-27: QR code validity window in minutes. Default 15 minutes.
         */
        private int qrExpiryMinutes = 15;

        /**
         * ATS-27: Redis TTL (seconds) for payment status cache. Default 960s (15 min + 1 min buffer).
         */
        private int statusCacheTtlSeconds = 960;
    }

    /**
     * ATS-30: Scheduler configuration for automatic order expiry and reserve release.
     */
    private Scheduler scheduler = new Scheduler();

    @Getter
    @Setter
    public static class Scheduler {
        /**
         * ATS-30: Cron expression for the payment expiry check job.
         * Default: every 1 minute. Override per environment via application.yml.
         */
        private String paymentExpiryCron = "0 */1 * * * *";
    }
}
