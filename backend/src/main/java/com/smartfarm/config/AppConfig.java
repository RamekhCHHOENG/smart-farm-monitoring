package com.smartfarm.config;

import com.smartfarm.notification.ConsoleNotifier;
import com.smartfarm.notification.NotificationService;
import com.smartfarm.notification.TelegramNotifier;
import com.smartfarm.web.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig implements WebMvcConfigurer {

    @Value("${farm.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    private final AuthInterceptor authInterceptor;

    public AppConfig(@Lazy AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /** Injected clock so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wiring point for Dependency Inversion: switching from console to Telegram
     * is a configuration change, not a code change in MonitoringService.
     * The token and chat id come only from environment variables.
     */
    @Bean
    public NotificationService notificationService(RestClient.Builder builder,
                                                   @Value("${farm.telegram.bot-token:}") String botToken,
                                                   @Value("${farm.telegram.chat-id:}") String chatId) {
        if (!botToken.isBlank() && !chatId.isBlank()) {
            return new TelegramNotifier(builder, botToken, chatId);
        }
        return new ConsoleNotifier();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Devices authenticate with the device key instead of a user login.
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/devices/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("*");
    }
}
