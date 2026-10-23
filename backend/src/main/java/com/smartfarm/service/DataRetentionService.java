package com.smartfarm.service;

import com.smartfarm.repository.SensorReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Keeps the database small: a sensor every 5 seconds is about 17,000 readings a day.
 * Readings older than the retention period are deleted; alerts and device logs are kept.
 */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final SensorReadingRepository readings;
    private final Clock clock;
    private final Duration keep;

    public DataRetentionService(SensorReadingRepository readings, Clock clock,
                                @Value("${farm.reading-retention-days:90}") int days) {
        this.readings = readings;
        this.clock = clock;
        this.keep = Duration.ofDays(days);
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    @Transactional
    public int deleteOldReadings() {
        int deleted = readings.deleteOlderThan(clock.instant().minus(keep));
        if (deleted > 0) {
            log.info("Deleted {} readings older than {} days", deleted, keep.toDays());
        }
        return deleted;
    }
}
