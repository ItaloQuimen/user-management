package com.imqh.usermanagementapi.service;

import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;

public interface UserService {
    UserResponse registerUser(UserRequest userRequest);
}
