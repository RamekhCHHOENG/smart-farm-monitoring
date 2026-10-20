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
    void cooldownPreventsRepeatedAlerts() {
        dryRule.markTriggered(now);
        Instant soon = now.plus(Duration.ofMinutes(AutomationRule.DEFAULT_COOLDOWN_MINUTES - 1));
        Instant later = now.plus(Duration.ofMinutes(AutomationRule.DEFAULT_COOLDOWN_MINUTES));
        assertThat(dryRule.isTriggered(data.soil.record(10, soon), soon)).isFalse();
        assertThat(dryRule.isTriggered(data.soil.record(10, later), later)).isTrue();
    }
}
