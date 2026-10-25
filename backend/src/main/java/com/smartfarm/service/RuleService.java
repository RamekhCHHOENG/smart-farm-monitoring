package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.AutomationRuleRepository;
import com.smartfarm.repository.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UC-4 Configure Automation Rule (FR-05). Only the Farm Owner may change rules. */
@Service
@Transactional
public class RuleService {

    /** {@code stopAt} null = run for a fixed time; a value = keep the range (stop when the reading gets back there). */
    public record RuleCommand(String name, Long sensorId, Condition condition, double threshold,
                              Long actuatorId, int durationMin, Severity severity, Integer cooldownMinutes, Double stopAt) {
    }

    private final AutomationRuleRepository rules;
    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final AccessPolicy access;

    private final Tenancy tenancy;


    public RuleService(AutomationRuleRepository rules, SensorRepository sensors,
                       ActuatorRepository actuators, AccessPolicy access, Tenancy tenancy) {

        this.tenancy = tenancy;
        this.rules = rules;
        this.sensors = sensors;
        this.actuators = actuators;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<AutomationRule> list() {
        return tenancy.limited() ? rules.findBySensor_Zone_Farm_IdOrderByIdAsc(tenancy.farmId()) : rules.findAllByOrderByIdAsc();
    }

    public AutomationRule create(Long userId, RuleCommand cmd) {
        access.require(userId);
        Sensor sensor = tenancy.sensor(sensors.findById(cmd.sensorId()).orElseThrow(() -> new NotFoundException("Sensor", cmd.sensorId())));
        checkThreshold(sensor, cmd.threshold());
        Actuator actuator = actuator(cmd, sensor);
        AutomationRule rule = new AutomationRule(cmd.name(), sensor, cmd.condition(), cmd.threshold(),
                actuator, cmd.durationMin(), cmd.severity());
        if (cmd.cooldownMinutes() != null) {
            rule.update(cmd.name(), cmd.condition(), cmd.threshold(), actuator, cmd.durationMin(), cmd.severity(), cmd.cooldownMinutes());
        }
        rule.holdUntil(cmd.stopAt());
        checkNoFight(rule);
        return rules.save(rule);
    }

    public AutomationRule update(Long userId, Long ruleId, RuleCommand cmd) {
        access.require(userId);
        AutomationRule rule = get(ruleId);
        checkThreshold(rule.getSensor(), cmd.threshold());
        rule.update(cmd.name(), cmd.condition(), cmd.threshold(), actuator(cmd, rule.getSensor()), cmd.durationMin(),
                cmd.severity(), cmd.cooldownMinutes() != null ? cmd.cooldownMinutes() : rule.getCooldownMinutes());
        rule.holdUntil(cmd.stopAt());
        checkNoFight(rule);
        return rule;
    }

    public AutomationRule setActive(Long userId, Long ruleId, boolean active) {
        access.require(userId);
        AutomationRule rule = get(ruleId);
        rule.setActive(active);
        return rule;
    }

    public void delete(Long userId, Long ruleId) {
        access.require(userId);
        rules.delete(get(ruleId));
    }

    /**
     * Two rules on one sensor that push in opposite directions (fan above 34, heater below 20) must leave a
     * quiet band between them. If the heater kept heating past where the fan starts, they would take turns
     * forever: heater on, fan on, heater on... Each rule must stop before the other one's start.
     */
    private void checkNoFight(AutomationRule rule) {
        if (rule.getActuator() == null) {
            return;
        }
        for (AutomationRule other : rules.findBySensor_Id(rule.getSensor().getId())) {
            if (other == rule || other.getId() != null && other.getId().equals(rule.getId())
                    || other.getActuator() == null || other.getCondition() == rule.getCondition()) {
                continue;
            }
            AutomationRule upper = rule.getCondition() == Condition.ABOVE ? rule : other;
            AutomationRule lower = upper == rule ? other : rule;
            String unit = rule.getSensor().getUnit();
            if (lower.getStopValue() >= upper.getThreshold()) {
                throw new IllegalArgumentException("\"%s\" runs %s until %s%s, but \"%s\" starts %s above %s%s: they would fight. Make it stop below %s%s."
                        .formatted(lower.getName(), lower.getActuator().getName(), format(lower.getStopValue()), unit,
                                upper.getName(), upper.getActuator().getName(), format(upper.getThreshold()), unit,
                                format(upper.getThreshold()), unit));
            }
            if (upper.getStopValue() <= lower.getThreshold()) {
                throw new IllegalArgumentException("\"%s\" runs %s down to %s%s, but \"%s\" starts %s below %s%s: they would fight. Make it stop above %s%s."
                        .formatted(upper.getName(), upper.getActuator().getName(), format(upper.getStopValue()), unit,
                                lower.getName(), lower.getActuator().getName(), format(lower.getThreshold()), unit,
                                format(lower.getThreshold()), unit));
            }
        }
    }

    /** A threshold the sensor can never report would make the rule fire on every reading, or never. */
    private static void checkThreshold(Sensor sensor, double threshold) {
        if (threshold < sensor.getMinValid() || threshold > sensor.getMaxValid()) {
            throw new IllegalArgumentException("Threshold must be between %s and %s %s for %s".formatted(
                    format(sensor.getMinValid()), format(sensor.getMaxValid()), sensor.getUnit(), sensor.getName()));
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private Actuator actuator(RuleCommand cmd, Sensor sensor) {
        if (cmd.actuatorId() == null) {
            return null;
        }
        Actuator actuator = tenancy.actuator(actuators.findById(cmd.actuatorId()).orElseThrow(() -> new NotFoundException("Actuator", cmd.actuatorId())));
        if (!actuator.getZone().getId().equals(sensor.getZone().getId())) {
            throw new IllegalArgumentException("Sensor and actuator must be in the same zone");
        }
        if (cmd.durationMin() <= 0 || cmd.durationMin() > actuator.getMaxRunMinutes()) {
            throw new IllegalArgumentException("Run time must be 1-" + actuator.getMaxRunMinutes() + " minutes for " + actuator.getName());
        }
        return actuator;
    }

    private AutomationRule get(Long ruleId) {
        return tenancy.rule(rules.findById(ruleId).orElseThrow(() -> new NotFoundException("Rule", ruleId)));
    }
}
