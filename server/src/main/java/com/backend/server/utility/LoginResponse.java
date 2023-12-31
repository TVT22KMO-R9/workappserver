package com.backend.server.utility;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String token;
    private String refreshToken;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private Role role;
    private String companyname;
    private Map<String, Object> companySettings;
}
