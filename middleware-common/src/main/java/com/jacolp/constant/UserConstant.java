package com.jacolp.constant;

public class UserConstant {
    public static final String ADMIN_ID_CLAIM = "adminId";
    public static final String USER_ID_CLAIM = "userId";

    /**
     * 用户状态
     */
    public static final int DELETED_STATUS = -1;
    public static final int UNACTIVE_STATUS = 2;
    public static final int ACTIVE_STATUS = 1;
    public static final int BANNED_STATUS = 0;

    /**
     * 激活用户账号状态使用的常量
     */
    public static final String JWT_NOT_VALID = "无效的激活链接";
    public static final String ACTIVE_SIGN_KEY = "active";

    public static final String USER_LOGIN_FAILED = "用户登录失败";
    public static final String NOT_FIND_USER = "用户不存在";
    public static final String USER_IS_BANNED = "用户已封禁";
    public static final String USER_PASSWORD_ERROR = "用户密码错误";
    public static final String PERMISSION_DENIED = "无权限";
    public static final String USERNAME_IS_REQUIRED = "用户名不能为空";
    public static final String PASSWORD_IS_REQUIRED = "密码不能为空";
    public static final String PASSWORD_CONFIRM_ERROR = "两次密码输入不一致";
    public static final String USER_ALREADY_EXISTS = "用户名已存在";
    public static final String UPDATE_USER_STORAGE_FAILED = "更新用户存储空间失败";
    public static final String USERNAME_AND_PASSWORD_PROVIDER_ERROR = "校验用户密码时发生错误";
    public static final String USER_ALREADY_ACTIVE = "用户已激活";
    public static final String UPDATE_USER_INFO_FAILED = "更新用户信息失败";
    public static final String USER_EMAIL_IS_EMPTY = "用户邮箱不能为空";
}
