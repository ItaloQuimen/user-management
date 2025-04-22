package com.imqh.usermanagementapi.service.impl;

import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.entity.Phone;
import com.imqh.usermanagementapi.entity.User;
import com.imqh.usermanagementapi.exception.CustomException;
import com.imqh.usermanagementapi.repository.UserRepository;
import com.imqh.usermanagementapi.service.UserService;
import com.imqh.usermanagementapi.util.JwtUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final Pattern passwordPattern;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           JwtUtil jwtUtil,
                           @Value("${app.password.regex}") String passwordRegex) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordPattern = Pattern.compile(passwordRegex);
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    @Transactional
    public UserResponse registerUser(UserRequest userRequest) {
        if (userRepository.findByEmail(userRequest.getEmail()).isPresent()) {
            throw new CustomException("El correo ya registrado");
        }

        if (!passwordPattern.matcher(userRequest.getPassword()).matches()) {
            throw new CustomException("La contraseña no cumple con el formato requerido");
        }

        User user = new User();
        user.setName(userRequest.getName());
        user.setEmail(userRequest.getEmail());

        String encodedPassword = passwordEncoder.encode(userRequest.getPassword());
        user.setPassword(encodedPassword);

        if (userRequest.getPhones() != null) {
            user.setPhones(userRequest.getPhones().stream().map(phoneReq -> {
                Phone phone = new Phone();
                phone.setNumber(phoneReq.getNumber());
                phone.setCitycode(phoneReq.getCitycode());
                phone.setContrycode(phoneReq.getContrycode());
                phone.setUser(user);
                return phone;
            }).collect(Collectors.toList()));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        user.setToken(token);

        User savedUser = userRepository.save(user);

        UserResponse response = new UserResponse();
        response.setId(savedUser.getId());
        response.setCreated(savedUser.getCreated());
        response.setModified(savedUser.getModified());
        response.setLastLogin(savedUser.getLastLogin());
        response.setToken(savedUser.getToken());
        response.setIsActive(savedUser.isActive());
        return response;
    }
}
