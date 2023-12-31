package com.backend.server.security.DTO;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePassWordDTO {
    
    @NotBlank(message="Must put in password")
    @Size(min = 8, message="Password must be at least 8 characters long")
    private String newPassword;


}
