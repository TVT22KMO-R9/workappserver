package com.backend.server.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.server.reportedhours.WorkDayRepository;
import com.backend.server.security.DTO.RegisterDTO;
import com.backend.server.users.User;
import com.backend.server.users.UserRepository;
import com.backend.server.utility.HolidayChecker;
import com.backend.server.utility.LoginResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@CrossOrigin
@RestController
@RequestMapping("/api")  // kaikki alkaa /api + endpointin /osote
public class SecurityRestController {
    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final HolidayChecker holidayChecker;
    private final WorkDayRepository workDayRepository;


    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterDTO registerDTO){
    try {
        User registeredUser = securityService.register(registerDTO.getEmail(), registerDTO.getPassword(), registerDTO.getFirstName(), registerDTO.getLastName(), registerDTO.getPhoneNumber());
        if (registeredUser != null) {
            return ResponseEntity.ok("User created successfully");
        } else {
            return ResponseEntity.badRequest().body("User creation failed");
        }
    } catch (IllegalArgumentException e) {
        // Catch argumentexceptionille
        return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
        // Jos tarvitsee debugata jotain muuta niin 500
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred");
    }
}


    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestHeader("Authorization") String basicAuth) {
        try {
            String basicStart = "Basic ";
            
            if (!basicAuth.startsWith(basicStart)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing Basic Auth format");
            }

            String base64Credentials = basicAuth.substring(basicStart.length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            String[] values = credentials.split(":", 2);

            if (values.length != 2) {
                return ResponseEntity.badRequest().body("Error in Basic Auth format");
            }

            String email = values[0];
            String password = values[1];

            LoginResponse response = securityService.login(email, password);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // Kutsuttu functio käyttää throw new IllegalArgumentExceptionia, josta viesti
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }


    @PostMapping("/refresh")  // frontti koodattava reagoimaan accesstokenin vanhenemiseen niin että yrittää tähän endpointiin refreshtokenilla ja saada uuden tokenin
    public ResponseEntity<String> refresh(@Valid @RequestHeader("Authorization") String refreshToken) {
        try {
            String newAccessToken = securityService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(newAccessToken);
        } catch (IllegalArgumentException e) {
            // Kutsuttu functio käyttää throw new IllegalArgumentExceptionia
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

}