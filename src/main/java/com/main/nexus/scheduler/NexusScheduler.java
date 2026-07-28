package com.main.nexus.scheduler;

import com.main.nexus.service.MatchExpirationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NexusScheduler {

    @Autowired
    private MatchExpirationService matchExpirationService;

    @Scheduled(cron = "0 0 0 * * *")
    public void runMatchExpirationCheck() {
        matchExpirationService.checkAndExpireMatches();
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void runMatchApproachingCheck() {
        matchExpirationService.checkAndNotifyApproaching();
    }
}
