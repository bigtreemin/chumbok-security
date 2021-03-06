package com.chumbok.security.config;

import com.chumbok.security.properties.SecurityProperties;
import com.chumbok.security.util.AuthTokenParser;
import com.chumbok.security.util.EncryptionKeyUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.Cookie;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AbstractChumbokSecurityConfigIT.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("it")
public class AbstractChumbokSecurityConfigIT {

    /**
     * JWT auth token created with private_key.der
     */
    private final String AUTH_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsIm9yZyI6IkNodW1ib2siLCJ0ZW5hbnQiOiJDaHVtYm9rIiwic2NvcGVzIjpbIlJPTEVfU1VQRVJBRE1JTiJdLCJpc3MiOiJDaHVtYm9rIiwiaWF0IjoxNTM3MjkxMzQ1LCJleHAiOjI1MjcyOTEzNDV9.EvprrNSe62UphCBivgOH2lcaxHtBy449-kazyLm1HYCS2dYcHSqY1QOsRJnV7PGaeHofHD4WnJfIkQbkZ5_hEL0T5dUBdSbVuNhe-aoSJH_UpUwPyRMcy0LH5IVxfS7hVoq67IvuPDHmwDM6gdo6NjshNi9po3EEhmVMNKLlzNCu62bu8jXVtpekTNRTmlDHiGZToKjYBqafaAEGzGktb7GzBwbqURum2lWF0Wabk4q8ZYJFpV7qr3gkklkSCqEYhy-a9DP3CFDl_zEfg168ejKjCpNRYhFEWtg90t6iH7iMgd2uDZnEHgbBbLpc5VXWbx4xJlMbCB4fkRZ8lHl4wQ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Before
    public void setup() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @Test
    public void shouldReturn403OnMissingAuthorizationHeaderOrCookie() throws Exception {

        mockMvc.perform(get("/"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.code").value("FORBIDDEN_REQUEST"))
                .andExpect(jsonPath("$.message").value("Permission denied for the resource."))
                .andDo(print());
    }

    @Test
    public void shouldReturn200OnPostRequestWithValidAuthorizationHeader() throws Exception {

        MvcResult result = mockMvc.perform(get("/")
                .header("Authorization", "Bearer+" + AUTH_TOKEN))
                .andReturn();
        String xsrfToken = result.getResponse().getCookie("XSRF-TOKEN").getValue();

        mockMvc.perform(post("/")
                .header("Authorization", "Bearer+" + AUTH_TOKEN)
                .header("X-XSRF-TOKEN", xsrfToken)
                .cookie(new Cookie("XSRF-TOKEN", xsrfToken)))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    public void shouldReturn200OnValidAuthorizationHeader() throws Exception {

        mockMvc.perform(get("/").header("Authorization", "Bearer+" + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    public void shouldReturn200OnValidAuthorizationCookie() throws Exception {

        mockMvc.perform(get("/").cookie(new Cookie("Authorization", AUTH_TOKEN)))
                .andExpect(status().isOk())
                .andDo(print());

    }

    @Test
    public void shouldReturnAuthenticationInfoOnValidSecurityContext() throws Exception {

        mockMvc.perform(get("/authentication")
                .cookie(new Cookie("Authorization", "Bearer+" + AUTH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.principle").value("Chumbok13Chumbok13admin"))
                .andExpect(jsonPath("$.isAuthenticated").value("true"))
                .andExpect(jsonPath("$.authorities").value("[ROLE_SUPERADMIN]"))
                .andDo(print());


    }

    @Test
    public void shouldReturnXsrfCookie() throws Exception {

        mockMvc.perform(get("/").header("Authorization", "Bearer+" + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andDo(print());
    }

    @EnableWebSecurity
    static class SecurityConfig extends AbstractSecurityConfig {

        @Bean(name = "authenticationManager")
        public AuthenticationManager authenticationManagerBean() throws Exception {
            return super.authenticationManagerBean();
        }

        @Bean
        public AuthTokenParser authTokenParser() throws IOException {
            ClassLoader classLoader = getClass().getClassLoader();
            File resource = new File(classLoader.getResource("public_key.der").getFile());
            EncryptionKeyUtil encryptionKeyUtil = new EncryptionKeyUtil();
            return new AuthTokenParser(encryptionKeyUtil.loadPublicKey(resource.toPath().toString()));
        }

        @Autowired
        @Override
        protected void setAuthTokenParser(AuthTokenParser authTokenParser) {
            super.setAuthTokenParser(authTokenParser);
        }

        @Bean
        public SecurityProperties securityProperties() {
            SecurityProperties securityProperties = new SecurityProperties();
            securityProperties.setEnable(true);
            securityProperties.setAssertOrgWith("Chumbok");
            securityProperties.setAssertTenant(true);
            securityProperties.setAssertTenantWith("Chumbok");
            return securityProperties;
        }

        @Autowired
        @Override
        protected void setSecurityProperties(SecurityProperties securityProperties) {
            super.setSecurityProperties(securityProperties);
        }
    }

    @SpringBootApplication
    @PropertySource("classpath:application.yml")
    @RestController
    static class Application {

        @GetMapping("/")
        public void ping() {
        }

        @PostMapping("/")
        public void postSomething() {
        }

        @RequestMapping("/authentication")
        public Map<String, String> authentication() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Map<String, String> authMap = new HashMap<>();
            authMap.put("principle", (String) authentication.getPrincipal());
            authMap.put("isAuthenticated", String.valueOf(authentication.isAuthenticated()));
            authMap.put("authorities", authentication.getAuthorities().toString());
            return authMap;
        }
    }

}
