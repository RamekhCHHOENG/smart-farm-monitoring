package com.smartfarm.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies every transition of the Deliverable 9 state machine. */
class ActuatorStateMachineTest {

    private final Actuator pump = new DomainTestData().pump;
    private final Instant now = Instant.parse("2026-10-20T08:00:00Z");

    @Test
    void installedActuatorStartsIdle() {
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.IDLE);
    }

    @Test
    void activateRespectsSafetyLimit() {
        assertThat(pump.activate(0, now)).isFalse();
        assertThat(pump.activate(pump.getMaxRunMinutes() + 1, now)).isFalse();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.IDLE);

        assertThat(pump.activate(3, now)).isTrue();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.RUNNING);
    }

    @Test
    void runningActuatorCannotBeStartedTwice() {
        pump.activate(3, now);
        assertThat(pump.activate(3, now)).isFalse();
    }

    @Test
    void runExpiresAfterRequestedDuration() {
        pump.activate(3, now);
        assertThat(pump.isRunExpired(now.plus(Duration.ofMinutes(2)))).isFalse();
        assertThat(pump.isRunExpired(now.plus(Duration.ofMinutes(3)))).isTrue();
    }

    @Test
    void deactivateOnlyWorksWhileRunning() {
        assertThat(pump.deactivate()).isFalse();
        pump.activate(3, now);
        assertThat(pump.deactivate()).isTrue();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.IDLE);

        // Midterm bug fixed: a faulty pump must not jump back to IDLE through deactivate().
        pump.reportFault();
        assertThat(pump.deactivate()).isFalse();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.FAULT);
    }

    @Test
    void faultRepairCycle() {
        pump.activate(3, now);
        pump.reportFault();
        assertThat(pump.activate(3, now)).isFalse();

        pump.startRepair();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.MAINTENANCE);
        assertThat(pump.completeRepair(false)).isFalse();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.MAINTENANCE);
        assertThat(pump.completeRepair(true)).isTrue();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.IDLE);
    }

    @Test
    void offlineCanHappenWhileRunningAndRecover() {
        pump.activate(3, now);
        pump.heartbeatLost();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.OFFLINE);
        pump.heartbeatRestored();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.IDLE);
    }

    @Test
    void decommissionIsFinalAndOnlyFromMaintenance() {
        assertThatThrownBy(pump::decommission).isInstanceOf(IllegalStateException.class);
        pump.reportFault();
        pump.startRepair();
        pump.decommission();
        assertThat(pump.getStatus()).isEqualTo(ActuatorStatus.DECOMMISSIONED);
        assertThatThrownBy(pump::startRepair).isInstanceOf(IllegalStateException.class);
        assertThat(pump.activate(1, now)).isFalse();
    }
}
