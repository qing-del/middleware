package com.jacolp.pojo.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端更新自身信息的请求 DTO。
 * 昵称、邮箱为可选字段；maxStorageBytes 仅在具备对应权限时允许修改。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户昵称（可选） */
    private String nickname;

    /** 邮箱地址（可选） */
    private String email;

    /** 用户个性化最大存储空间(字节)，仅管理员/创建者可修改 */
    private Long maxStorageBytes;
}