package com.imqh.usermanagementapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserRequest validRequest;

    @BeforeEach
    public void setUp() {
        validRequest = new UserRequest();
        validRequest.setName("Test User");
        validRequest.setEmail("test@example.com");
        validRequest.setPassword("Password1");
        validRequest.setPhones(Collections.emptyList());
    }

    @Test
    public void registerUser_success() throws Exception {
        UserResponse response = new UserResponse();
        response.setId("1234");
        response.setToken("dummy-token");
        response.setIsActive(true);

        when(userService.registerUser(validRequest)).thenReturn(response);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1234"))
                .andExpect(jsonPath("$.token").value("dummy-token"));
    }
}
