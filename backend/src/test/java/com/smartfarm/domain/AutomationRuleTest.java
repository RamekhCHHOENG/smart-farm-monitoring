package com.smartfarm.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationRuleTest {

    private final DomainTestData data = new DomainTestData();
    private final Instant now = Instant.parse("2026-10-20T08:00:00Z");
    private final AutomationRule dryRule =
            new AutomationRule("Dry", data.soil, Condition.BELOW, 30, data.pump, 3, Severity.WARNING);

    @Test
    void triggersOnlyWhenThresholdIsCrossed() {
        assertThat(dryRule.isTriggered(data.soil.record(29.9, now), now)).isTrue();
        assertThat(dryRule.isTriggered(data.soil.record(30, now), now)).isFalse();
        assertThat(dryRule.isTriggered(data.soil.record(55, now), now)).isFalse();
    }

    @Test
    void aboveConditionUsesStrictComparison() {
        AutomationRule wet = new AutomationRule("Wet", data.soil, Condition.ABOVE, 80, null, 0, Severity.INFO);
        assertThat(wet.isTriggered(data.soil.record(80.1, now), now)).isTrue();
        assertThat(wet.isTriggered(data.soil.record(80, now), now)).isFalse();
    }

    @Test
    void inactiveRuleNeverTriggers() {
        dryRule.setActive(false);
        assertThat(dryRule.isTriggered(data.soil.record(5, now), now)).isFalse();
    }

    @Test
    void keepRangeRuleStopsOnlyWhenTheReadingIsBack() {
        dryRule.holdUntil(45.0);
        assertThat(dryRule.keepsRange()).isTrue();
        assertThat(dryRule.isTargetReached(data.soil.record(44.9, now))).isFalse();
        assertThat(dryRule.isTargetReached(data.soil.record(45, now))).isTrue();

        dryRule.holdUntil(null); // back to a fixed run time
        assertThat(dryRule.isTargetReached(data.soil.record(80, now))).isFalse();
    }

    @Test
    void stopValueMustBeOnTheComfortableSide() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dryRule.holdUntil(25.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be above 30");
        AutomationRule wet = new AutomationRule("Wet", data.soil, Condition.ABOVE, 80, data.pump, 3, Severity.INFO);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> wet.holdUntil(85.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be below 80");
        AutomationRule alertOnly = new AutomationRule("Alert", data.soil, Condition.ABOVE, 80, null, 0, Severity.INFO);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> alertOnly.holdUntil(70.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs an actuator");
    }

    @Test
    void cooldownPreventsRepeatedAlerts() {
        dryRule.markTriggered(now);
        Instant soon = now.plus(Duration.ofMinutes(AutomationRule.DEFAULT_COOLDOWN_MINUTES - 1));
        Instant later = now.plus(Duration.ofMinutes(AutomationRule.DEFAULT_COOLDOWN_MINUTES));
        assertThat(dryRule.isTriggered(data.soil.record(10, soon), soon)).isFalse();
        assertThat(dryRule.isTriggered(data.soil.record(10, later), later)).isTrue();
    }
}
