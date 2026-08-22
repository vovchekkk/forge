package com.forgeci.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forge")
public class ForgeProperties {

    private final Scheduler scheduler = new Scheduler();
    private final Runner runner = new Runner();
    private final Security security = new Security();

    public Scheduler getScheduler() { return scheduler; }
    public Runner getRunner() { return runner; }
    public Security getSecurity() { return security; }

    public static class Scheduler {
        
        private Duration interval = Duration.ofSeconds(1);

        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
    }

    public static class Runner {
        
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        
        private Duration offlineThreshold = Duration.ofSeconds(30);

        public Duration getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
        public Duration getOfflineThreshold() { return offlineThreshold; }
        public void setOfflineThreshold(Duration offlineThreshold) { this.offlineThreshold = offlineThreshold; }
    }

    public static class Security {
        private final Jwt jwt = new Jwt();
        private final Login login = new Login();
        private final Cors cors = new Cors();

        public Jwt getJwt() { return jwt; }
        public Login getLogin() { return login; }
        public Cors getCors() { return cors; }
    }

    public static class Cors {
        
        private java.util.List<String> allowedOrigins = java.util.List.of();

        public java.util.List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(java.util.List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Jwt {
        
        private String secret = "";
        
        private String issuer = "forge-ci";
        
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        
        private Duration refreshTokenTtl = Duration.ofDays(30);

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public Duration getAccessTokenTtl() { return accessTokenTtl; }
        public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
        public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
        public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    }

    public static class Login {
        
        private int maxAttempts = 5;
        
        private Duration lockoutWindow = Duration.ofMinutes(10);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getLockoutWindow() { return lockoutWindow; }
        public void setLockoutWindow(Duration lockoutWindow) { this.lockoutWindow = lockoutWindow; }
    }
}