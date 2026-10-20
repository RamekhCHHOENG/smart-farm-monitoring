package com.smartfarm.web;

import com.smartfarm.service.RuleService;
import com.smartfarm.service.RuleService.RuleCommand;
import com.smartfarm.service.TaskService;
import com.smartfarm.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Automation rules (UC-4) and scheduled tasks (UC-5). */
@RestController
@RequestMapping("/api")
@Transactional
public class AutomationController {

    private final RuleService ruleService;
    private final TaskService taskService;
    private final Clock clock;

    public AutomationController(RuleService ruleService, TaskService taskService, Clock clock) {
        this.ruleService = ruleService;
        this.taskService = taskService;
        this.clock = clock;
    }

    @GetMapping("/rules")
    @Transactional(readOnly = true)
    public List<RuleDto> rules() {
        return ruleService.list().stream().map(RuleDto::of).toList();
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleDto createRule(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody RuleRequest req) {
        return RuleDto.of(ruleService.create(userId, command(req)));
    }

    @PutMapping("/rules/{id}")
    public RuleDto updateRule(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                              @Valid @RequestBody RuleRequest req) {
        return RuleDto.of(ruleService.update(userId, id, command(req)));
    }

    @PatchMapping("/rules/{id}/active")
    public RuleDto setRuleActive(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                 @RequestBody ActiveRequest req) {
        return RuleDto.of(ruleService.setActive(userId, id, req.active()));
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        ruleService.delete(userId, id);
    }

    @GetMapping("/tasks")
    @Transactional(readOnly = true)
    public List<TaskDto> tasks() {
        return taskService.list().stream().map(t -> TaskDto.of(t, now())).toList();
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto createTask(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody TaskRequest req) {
        return TaskDto.of(taskService.create(userId, req.name(), req.actuatorId(), req.runTime(), req.repeatDays(), req.durationMin()), now());
    }

    @PatchMapping("/tasks/{id}/active")
    public TaskDto setTaskActive(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                 @RequestBody ActiveRequest req) {
        return TaskDto.of(taskService.setActive(userId, id, req.active()), now());
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        taskService.delete(userId, id);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static RuleCommand command(RuleRequest req) {
        return new RuleCommand(req.name(), req.sensorId(), req.condition(), req.threshold(), req.actuatorId(),
                req.durationMin(), req.severity(), req.cooldownMinutes());
    }
}
