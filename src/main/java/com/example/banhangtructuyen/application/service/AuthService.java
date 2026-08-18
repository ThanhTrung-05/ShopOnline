package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.LoginRequest;
import com.example.banhangtructuyen.application.dto.auth.LoginResponse;
import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    /** Authenticates an account by email/password and returns a signed JWT. */
    LoginResponse login(LoginRequest request);
}
