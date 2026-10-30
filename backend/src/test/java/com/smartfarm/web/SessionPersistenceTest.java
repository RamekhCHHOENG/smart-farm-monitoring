package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.UserSessionRepository;
import com.smartfarm.service.AuthService;
import com.smartfarm.service.LoginGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sign-ins are kept in the database: a server restart no longer logs everyone out. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessions;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class SessionPersistenceTest {

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserSessionRepository sessions;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void aSessionOutlivesTheServerThatIssuedIt() throws Exception {
        String token = login();
        // A new AuthService has no memory of the login, like the server after a restart.
        AuthService restarted = fresh(Clock.systemUTC());
        assertThat(resolve(restarted, token)).isPresent();
        // Only the hash is stored, so a copy of the database cannot be used to sign in.
        assertThat(jdbc.queryForList("select token_hash from user_session", String.class))
                .isNotEmpty().doesNotContain(token).allMatch(h -> h.length() == 64);
    }

    @Test
    void logoutAndExpiryEndTheSession() throws Exception {
        String token = login();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        AuthService later = fresh(Clock.fixed(Instant.now().plus(Duration.ofHours(13)), ZoneOffset.UTC));
        assertThat(resolve(later, token)).isEmpty();
        long before = sessions.count();
        Integer deleted = tx.execute(s -> later.deleteExpiredSessions());
        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(sessions.count()).isLessThan(before);

        String second = login();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + second)).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + second)).andExpect(status().isUnauthorized());
        assertThat(resolve(fresh(Clock.systemUTC()), "not-a-token")).isEmpty();
    }

    private Optional<Long> resolve(AuthService auth, String token) {
        return tx.execute(s -> auth.resolve(token));
    }

    private AuthService fresh(Clock clock) {
        return new AuthService(users, sessions, encoder, clock, new LoginGuard(clock, 5, Duration.ofMinutes(15)), Duration.ofHours(12));
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
