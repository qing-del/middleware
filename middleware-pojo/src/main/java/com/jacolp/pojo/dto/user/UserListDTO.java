package com.jacolp.pojo.dto.user;

import com.jacolp.pojo.provider.PageParamProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserListDTO implements PageParamProvider {
    private Long id;
    private String username;
    private Integer status;
    private Long roleId;
    private Integer pageNum;
    private Integer pageSize;
}
