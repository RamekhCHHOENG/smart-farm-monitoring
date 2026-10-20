package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** A recurring task such as "feed at 07:00 every day". */
@Entity
public class ScheduledTask {

    /** A task still runs if the server was busy or restarted, up to this many minutes late. */
    public static final int GRACE_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Actuator actuator;

    private LocalTime runTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<DayOfWeek> repeatDays = EnumSet.noneOf(DayOfWeek.class);

    private int durationMin;
    private boolean active = true;
    private LocalDate lastRunDate;

    protected ScheduledTask() {
    }

    public ScheduledTask(String name, Actuator actuator, LocalTime runTime, Set<DayOfWeek> repeatDays, int durationMin) {
        if (repeatDays.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one day");
        }
        this.name = name;
        this.actuator = actuator;
        this.runTime = runTime;
        this.repeatDays = EnumSet.copyOf(repeatDays);
        this.durationMin = durationMin;
    }

    public boolean isDue(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        return active
                && repeatDays.contains(now.getDayOfWeek())
                && !time.isBefore(runTime)
                && time.isBefore(runTime.plusMinutes(GRACE_MINUTES))
                && (lastRunDate == null || lastRunDate.isBefore(now.toLocalDate()));
    }

    /** The next time this task will start, or null when it is paused. */
    public LocalDateTime nextRun(LocalDateTime now) {
        if (!active) {
            return null;
        }
        for (int day = 0; day <= 7; day++) {
            LocalDate date = now.toLocalDate().plusDays(day);
            boolean alreadyRan = lastRunDate != null && !lastRunDate.isBefore(date);
            boolean stillInWindow = day > 0 || now.toLocalTime().isBefore(runTime.plusMinutes(GRACE_MINUTES));
            if (repeatDays.contains(date.getDayOfWeek()) && !alreadyRan && stillInWindow) {
                return LocalDateTime.of(date, runTime);
            }
        }
        return null;
    }

    public void markRun(LocalDate date) {
        lastRunDate = date;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Actuator getActuator() { return actuator; }
    public LocalTime getRunTime() { return runTime; }
    public Set<DayOfWeek> getRepeatDays() { return EnumSet.copyOf(repeatDays); }
    public int getDurationMin() { return durationMin; }
    public boolean isActive() { return active; }
    public LocalDate getLastRunDate() { return lastRunDate; }
}
