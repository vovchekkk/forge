package com.forgeci.runner.config;

import com.forgeci.runner.service.JobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RunnerStartup {

    private static final Logger log = LoggerFactory.getLogger(RunnerStartup.class);

    private final JobRunner jobRunner;

    public RunnerStartup(JobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Forge Runner started, registering with server");
        jobRunner.register();
    }
}