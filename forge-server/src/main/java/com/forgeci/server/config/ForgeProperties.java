package com.forgeci.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forge")
public class ForgeProperties {

    private final Scheduler scheduler = new Scheduler();
    private final Runner runner = new Runner();

    public Scheduler getScheduler() { return scheduler; }
    public Runner getRunner() { return runner; }

    public static class Scheduler {
        /** How often the scheduler scans for ready jobs and stale runners. */
        private Duration interval = Duration.ofSeconds(1);

        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
    }

    public static class Runner {
        /** Heartbeat interval reported to runners. */
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        /** How long without heartbeat a runner is considered offline. */
        private Duration offlineThreshold = Duration.ofSeconds(30);

        public Duration getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
        public Duration getOfflineThreshold() { return offlineThreshold; }
        public void setOfflineThreshold(Duration offlineThreshold) { this.offlineThreshold = offlineThreshold; }
    }
}