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

    public record RuleCommand(String name, Long sensorId, Condition condition, double threshold,
                              Long actuatorId, int durationMin, Severity severity, Integer cooldownMinutes) {
    }

    private final AutomationRuleRepository rules;
    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final AccessPolicy access;

    public RuleService(AutomationRuleRepository rules, SensorRepository sensors,
                       ActuatorRepository actuators, AccessPolicy access) {
        this.rules = rules;
        this.sensors = sensors;
        this.actuators = actuators;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<AutomationRule> list() {
        return rules.findAllByOrderByIdAsc();
    }

    public AutomationRule create(Long userId, RuleCommand cmd) {
        access.require(userId);
        Sensor sensor = sensors.findById(cmd.sensorId()).orElseThrow(() -> new NotFoundException("Sensor", cmd.sensorId()));
        Actuator actuator = actuator(cmd, sensor);
        AutomationRule rule = new AutomationRule(cmd.name(), sensor, cmd.condition(), cmd.threshold(),
                actuator, cmd.durationMin(), cmd.severity());
        if (cmd.cooldownMinutes() != null) {
            rule.update(cmd.name(), cmd.condition(), cmd.threshold(), actuator, cmd.durationMin(), cmd.severity(), cmd.cooldownMinutes());
        }
        return rules.save(rule);
    }

    public AutomationRule update(Long userId, Long ruleId, RuleCommand cmd) {
        access.require(userId);
        AutomationRule rule = get(ruleId);
        rule.update(cmd.name(), cmd.condition(), cmd.threshold(), actuator(cmd, rule.getSensor()), cmd.durationMin(),
                cmd.severity(), cmd.cooldownMinutes() != null ? cmd.cooldownMinutes() : rule.getCooldownMinutes());
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

    private Actuator actuator(RuleCommand cmd, Sensor sensor) {
        if (cmd.actuatorId() == null) {
            return null;
        }
        Actuator actuator = actuators.findById(cmd.actuatorId()).orElseThrow(() -> new NotFoundException("Actuator", cmd.actuatorId()));
        if (!actuator.getZone().getId().equals(sensor.getZone().getId())) {
            throw new IllegalArgumentException("Sensor and actuator must be in the same zone");
        }
        if (cmd.durationMin() <= 0 || cmd.durationMin() > actuator.getMaxRunMinutes()) {
            throw new IllegalArgumentException("Duration must be 1-" + actuator.getMaxRunMinutes() + " min for " + actuator.getName());
        }
        return actuator;
    }

    private AutomationRule get(Long ruleId) {
        return rules.findById(ruleId).orElseThrow(() -> new NotFoundException("Rule", ruleId));
    }
}
