package com.imqh.usermanagementapi.service.impl;

import com.imqh.usermanagementapi.dto.request.PhoneRequest;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.entity.User;
import com.imqh.usermanagementapi.exception.CustomException;
import com.imqh.usermanagementapi.exception.DuplicateEmailException;
import com.imqh.usermanagementapi.repository.UserRepository;
import com.imqh.usermanagementapi.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, jwtUtil,
                "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");
    }

    @Test
    void registerUser_mapsSavedUserAndPhonesAndPreservesBCrypt() {
        UserRequest request = request();
        PhoneRequest phone = new PhoneRequest();
        phone.setNumber("1234567");
        phone.setCitycode("1");
        phone.setContrycode("56");
        request.setPhones(List.of(phone));
        when(jwtUtil.generateToken(anyString(), eq(request.getEmail()))).thenReturn("dummy-token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UUID.fromString(user.getId());
            assertEquals(request.getName(), user.getName());
            assertEquals(request.getEmail(), user.getEmail());
            assertNotEquals(request.getPassword(), user.getPassword());
            assertTrue(new BCryptPasswordEncoder().matches(request.getPassword(), user.getPassword()));
            assertEquals(user.getCreated(), user.getModified());
            assertEquals(user.getCreated(), user.getLastLogin());
            assertEquals("dummy-token", user.getToken());
            assertTrue(user.isActive());
            assertEquals(1, user.getPhones().size());
            assertSame(user, user.getPhones().getFirst().getUser());
            assertEquals("1234567", user.getPhones().getFirst().getNumber());
            assertEquals("1", user.getPhones().getFirst().getCitycode());
            assertEquals("56", user.getPhones().getFirst().getContrycode());
            // Diferenciar la entrada del usuario guardado verifica el origen del mapeo.
            user.setName("Saved User");
            return user;
        });

        var response = userService.registerUser(request);
        UUID.fromString(response.getId());
        assertEquals("Saved User", response.getName());
        assertEquals(request.getEmail(), response.getEmail());
        assertEquals("1234567", response.getPhones().getFirst().number());
        assertEquals("1", response.getPhones().getFirst().cityCode());
        assertEquals("56", response.getPhones().getFirst().countryCode());
        assertEquals(response.getCreated(), response.getModified());
        assertEquals(response.getCreated(), response.getLastLogin());
        assertEquals("dummy-token", response.getToken());
        assertTrue(response.isActive());
        verify(jwtUtil).generateToken(response.getId(), response.getEmail());
    }

    @Test
    void registerUser_acceptsEmptyPhoneList() {
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("dummy-token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertTrue(userService.registerUser(request()).getPhones().isEmpty());
    }

    @Test
    void registerUser_emailAlreadyExistsThrowsSpecificException() {
        UserRequest request = request();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));
        var ex = assertThrows(DuplicateEmailException.class, () -> userService.registerUser(request));
        assertEquals("El correo ya registrado", ex.getMessage());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void registerUser_invalidPasswordIsDifferentFromDuplicateEmail() {
        UserRequest request = request();
        request.setPassword("hunter2");
        var ex = assertThrows(CustomException.class, () -> userService.registerUser(request));
        assertEquals("La contraseña no cumple con el formato requerido", ex.getMessage());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void registerUser_usesConfiguredPasswordRegex() {
        userService = new UserServiceImpl(userRepository, jwtUtil, "^Allowed!$");
        UserRequest request = request();
        request.setPassword("Password1");
        assertThrows(CustomException.class, () -> userService.registerUser(request));
        request.setPassword("Allowed!");
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("dummy-token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertNotNull(userService.registerUser(request).getId());
    }

    private UserRequest request() {
        UserRequest request = new UserRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("Password1");
        request.setPhones(List.of());
        return request;
    }
}
