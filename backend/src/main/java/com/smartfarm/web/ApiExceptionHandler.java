package com.smartfarm.web;

import com.smartfarm.service.ForbiddenException;
import com.smartfarm.service.NotFoundException;
import com.smartfarm.service.TooManyAttemptsException;
import com.smartfarm.service.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/** Maps domain errors to HTTP status codes with an RFC 9457 problem body. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ProblemDetail tooMany(TooManyAttemptsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** An invalid state-machine transition, e.g. starting a pump that is in FAULT. */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Malformed JSON, a missing field, or a value of the wrong type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is missing a field or has a wrong value");
    }

    /** e.g. an unknown lifecycle action or a text where a number is expected in the URL. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid value '" + e.getValue() + "' for " + e.getName());
    }

    /** Field names as people see them on the forms. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("name", "Name"), Map.entry("type", "Type"), Map.entry("location", "Location"),
            Map.entry("birdCount", "Number of birds"), Map.entry("cropName", "Crop"),
            Map.entry("sensorId", "Sensor"), Map.entry("actuatorId", "Actuator"), Map.entry("condition", "Condition"),
            Map.entry("threshold", "Threshold"), Map.entry("durationMin", "Run time (minutes)"),
            Map.entry("severity", "Severity"), Map.entry("cooldownMinutes", "Cooldown (minutes)"),
            Map.entry("maxRunMinutes", "Safety limit (minutes)"), Map.entry("runTime", "Time"),
            Map.entry("repeatDays", "Days"), Map.entry("value", "Value"),
            Map.entry("username", "Username"), Map.entry("password", "Password"),
            Map.entry("currentPassword", "Current password"), Map.entry("newPassword", "New password"),
            Map.entry("role", "Role"));

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> LABELS.getOrDefault(f.getField(), f.getField()) + ": " + friendly(f))
                .distinct()
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    private static String friendly(FieldError f) {
        String code = f.getCode();
        if (code == null) {
            return f.getDefaultMessage();
        }
        // Constraint attributes arrive sorted by name after the field itself: @Size → [field, max, min].
        Object[] args = f.getArguments();
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> "please fill this in";
            case "PositiveOrZero" -> "cannot be negative";
            case "Positive" -> "must be more than 0";
            case "Size" -> args != null && args.length > 1 ? "use at most " + args[1] + " characters" : f.getDefaultMessage();
            case "Max" -> args != null && args.length > 1 ? "must be at most " + args[1] : f.getDefaultMessage();
            default -> f.getDefaultMessage();
        };
    }
}
