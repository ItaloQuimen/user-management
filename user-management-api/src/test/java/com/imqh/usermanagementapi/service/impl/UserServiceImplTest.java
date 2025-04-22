package com.imqh.usermanagementapi.service.impl;

import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.entity.User;
import com.imqh.usermanagementapi.exception.CustomException;
import com.imqh.usermanagementapi.repository.UserRepository;
import com.imqh.usermanagementapi.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserServiceImpl(userRepository, jwtUtil,
                "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");
    }

    @Test
    void registerUser_success() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("Password1");
        request.setPhones(Collections.emptyList());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("dummy-token");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, "1234");
            return user;
        });

        UserResponse response = userService.registerUser(request);

        assertNotNull(response.getId());
        assertEquals("1234", response.getId());
        assertEquals("dummy-token", response.getToken());
        assertTrue(response.isIsActive());
    }
    
    @Test
    void registerUser_emailAlreadyExists_throwsException() {
        UserRequest request = new UserRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("Password1");
        request.setPhones(Collections.emptyList());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));

        CustomException ex = assertThrows(CustomException.class, () -> userService.registerUser(request));
        assertEquals("El correo ya registrado", ex.getMessage());
    }
}
