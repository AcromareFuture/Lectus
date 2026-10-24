package com.lectus.api;

import static com.lectus.api.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:identity-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.password="})
@AutoConfigureMockMvc
class IdentityIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db; @Autowired AuthService auth;
 Session session;
 @BeforeEach void setup(){session=auth.register(new Register(UUID.randomUUID()+"@example.test","safe-test-password","莱择测试"));}
 String encode(Object v)throws Exception{return json.writeValueAsString(v);}
 String token(){return "Bearer "+session.token();}
    @Test void registerLoginLogoutAndExpiredToken() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        var response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(encode(new Register(email,"correct-password","新用户"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode signed=json.readTree(response.getResponse().getContentAsString());
        String bearer="Bearer "+signed.path("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(encode(new Login(email,"wrong-password")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(encode(new Login(email,"correct-password")))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization",bearer)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization",bearer)).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_sessions SET expires_at=? WHERE account_id=?",OffsetDateTime.now().minusHours(1),session.user().id());
        mvc.perform(get("/api/auth/me").header("Authorization",token())).andExpect(status().isUnauthorized());
        String stored=db.queryForObject("SELECT password_hash FROM accounts WHERE id=?",String.class,session.user().id());
        assertNotEquals("safe-test-password",stored);assertTrue(stored.startsWith("$2a$"));
        assertNotEquals(session.token(),db.queryForObject("SELECT token_hash FROM auth_sessions WHERE account_id=?",String.class,session.user().id()));
    }
    @Test void loginIsRateLimitedAndDoesNotExposeDatabaseErrors() throws Exception {
        for(int i=0;i<20;i++) mvc.perform(post("/api/auth/login").with(request->{request.setRemoteAddr("192.0.2.88");return request;})
                .contentType(MediaType.APPLICATION_JSON).content(encode(new Login("missing@example.test","wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").with(request->{request.setRemoteAddr("192.0.2.88");return request;})
                .contentType(MediaType.APPLICATION_JSON).content(encode(new Login("missing@example.test","wrong-password"))))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}