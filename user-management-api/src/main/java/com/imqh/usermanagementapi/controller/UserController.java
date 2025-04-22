package com.imqh.usermanagementapi.controller;

import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRequest userRequest) {
        UserResponse response = userService.registerUser(userRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
