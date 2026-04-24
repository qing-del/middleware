package com.jacolp.pojo.dto.user;

import com.jacolp.pojo.provider.UsernameAndPasswordProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO implements UsernameAndPasswordProvider {
    private String username;
    private String password;
    private String confirmPassword;
}
