package com.smartfarm.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The sensor's own lifecycle: signal-driven ONLINE / OFFLINE, and the technician's FAULT → MAINTENANCE → ONLINE. */
class SensorStateMachineTest {

    private final Sensor soil = new DomainTestData().soil;
    private final Instant t0 = Instant.parse("2026-10-23T08:00:00Z");

    @Test
    void newSensorIsOnline() {
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.ONLINE);
        assertThat(soil.isOutOfService()).isFalse();
    }

    @Test
    void silenceMakesItOfflineAndTheNextReadingBringsItBack() {
        soil.record(40, t0);
        assertThat(soil.markOfflineIfSilentSince(t0.plusSeconds(120))).isTrue();
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.OFFLINE);

        soil.record(41, t0.plusSeconds(300));
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.ONLINE);
    }

    @Test
    void faultRepairCycleNeedsAFreshValidReading() {
        soil.record(40, t0);

        soil.reportFault(t0.plusSeconds(10));
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.FAULT);
        assertThat(soil.isOutOfService()).isTrue();

        soil.startRepair(t0.plusSeconds(60));
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.MAINTENANCE);
        assertThat(soil.isOutOfService()).isTrue();

        // The only reading is from before the repair began: not a proof
        assertThat(soil.completeRepair()).isFalse();
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.MAINTENANCE);

        soil.record(42, t0.plusSeconds(90));
        assertThat(soil.completeRepair()).isTrue();
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.ONLINE);
        assertThat(soil.isOutOfService()).isFalse();
    }

    @Test
    void anOfflineSensorCanBeRepairedWithoutReportingAFault() {
        soil.record(40, t0);
        soil.markOfflineIfSilentSince(t0.plusSeconds(200));

        soil.startRepair(t0.plusSeconds(300));
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.MAINTENANCE);
        soil.record(44, t0.plusSeconds(310));
        assertThat(soil.completeRepair()).isTrue();
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.ONLINE);
    }

    @Test
    void aSuspectReadingFailsTheTest() {
        soil.reportFault(t0);
        soil.startRepair(t0.plusSeconds(1));
        soil.record(40, t0.plusSeconds(5));
        soil.markSuspect(t0.plusSeconds(10)); // then an impossible value arrived

        assertThat(soil.completeRepair()).isFalse();
        soil.record(40, t0.plusSeconds(15));
        assertThat(soil.completeRepair()).isTrue();
    }

    @Test
    void aSensorInTheHandsOfATechnicianIsNeverReportedOffline() {
        soil.record(40, t0);
        soil.reportFault(t0.plusSeconds(1));
        assertThat(soil.markOfflineIfSilentSince(t0.plus(Duration.ofHours(1)))).isFalse();
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.FAULT);

        soil.startRepair(t0.plusSeconds(2));
        assertThat(soil.markOfflineIfSilentSince(t0.plus(Duration.ofHours(1)))).isFalse();
        assertThat(soil.getStatus()).isEqualTo(SensorStatus.MAINTENANCE);
    }

    @Test
    void transitionsOutsideTheStateMachineAreRefused() {
        assertThatThrownBy(() -> soil.startRepair(t0)).isInstanceOf(IllegalStateException.class); // healthy
        assertThatThrownBy(soil::completeRepair).isInstanceOf(IllegalStateException.class);       // not in repair

        soil.reportFault(t0);
        assertThatThrownBy(() -> soil.reportFault(t0)).isInstanceOf(IllegalStateException.class); // already faulty
        assertThatThrownBy(soil::completeRepair).isInstanceOf(IllegalStateException.class);       // repair not started

        soil.startRepair(t0);
        assertThatThrownBy(() -> soil.reportFault(t0)).isInstanceOf(IllegalStateException.class); // being repaired
        assertThatThrownBy(() -> soil.startRepair(t0)).isInstanceOf(IllegalStateException.class);
    }
}
