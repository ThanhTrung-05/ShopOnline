package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.auth.RegisterRequest;
import com.example.banhangtructuyen.application.dto.auth.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);
}
