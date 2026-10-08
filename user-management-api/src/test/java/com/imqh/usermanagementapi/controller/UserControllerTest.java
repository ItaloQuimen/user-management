package com.imqh.usermanagementapi.controller;

import tools.jackson.databind.json.JsonMapper;
import com.imqh.usermanagementapi.config.SecurityConfig;
import com.imqh.usermanagementapi.dto.request.PhoneRequest;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.util.Collections;
import java.time.LocalDateTime;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    private UserRequest validRequest;

    @BeforeEach
    public void setUp() {
        validRequest = new UserRequest();
        validRequest.setName("Test User");
        validRequest.setEmail("test@example.com");
        validRequest.setPassword("Password1");
        PhoneRequest phone = new PhoneRequest();
        phone.setNumber("1234567");
        phone.setCitycode("1");
        phone.setContrycode("56");
        validRequest.setPhones(Collections.singletonList(phone));
    }

    @Test
    public void registerUser_success() throws Exception {
        assertTrue(handlerAdapter.getMessageConverters().stream()
                .anyMatch(JacksonJsonHttpMessageConverter.class::isInstance));
        assertTrue(!securityFilterChain.getFilterChains().isEmpty());
        UserResponse response = new UserResponse();
        response.setId("1234");
        response.setToken("dummy-token");
        response.setIsActive(true);
        response.setCreated(LocalDateTime.of(2025, 4, 21, 22, 26, 43));
        response.setModified(LocalDateTime.of(2025, 4, 21, 22, 26, 44));
        response.setLastLogin(LocalDateTime.of(2025, 4, 21, 22, 26, 45));

        when(userService.registerUser(any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1234"))
                .andExpect(jsonPath("$.token").value("dummy-token"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.created").value("2025-04-21T22:26:43"))
                .andExpect(jsonPath("$.modified").value("2025-04-21T22:26:44"))
                .andExpect(jsonPath("$.lastLogin").value("2025-04-21T22:26:45"));

        ArgumentCaptor<UserRequest> captured = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).registerUser(captured.capture());
        UserRequest deserialized = captured.getValue();
        assertEquals(validRequest.getName(), deserialized.getName());
        assertEquals(validRequest.getEmail(), deserialized.getEmail());
        assertEquals(validRequest.getPassword(), deserialized.getPassword());
        assertEquals(1, deserialized.getPhones().size());
        assertEquals("1234567", deserialized.getPhones().getFirst().getNumber());
        assertEquals("1", deserialized.getPhones().getFirst().getCitycode());
        assertEquals("56", deserialized.getPhones().getFirst().getContrycode());
    }
}
