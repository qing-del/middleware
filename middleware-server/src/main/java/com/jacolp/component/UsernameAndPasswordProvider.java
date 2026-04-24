package com.jacolp.component;

import com.jacolp.constant.UserConstant;
import com.jacolp.exception.BaseException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户名与密码校验提供者
 * <p>
 * 集中管理用户名、密码的非空校验和一致性校验逻辑，
 * 供 UserServiceImpl 中多个方法复用，避免校验代码散落各处。
 * </p>
 */
@Component
public class UsernameAndPasswordProvider {

    /**
     * 校验用户名不能为空
     *
     * @param username 用户名
     * @throws BaseException 如果用户名为空
     */
    public void validateUsernameNotBlank(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }
    }

    /**
     * 校验密码不能为空
     *
     * @param password 密码明文
     * @throws BaseException 如果密码为空
     */
    public void validatePasswordNotBlank(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }
    }

    /**
     * 校验两次密码输入一致
     *
     * @param password        密码
     * @param confirmPassword 确认密码
     * @throws BaseException 如果两次密码不一致
     */
    public void validatePasswordConfirm(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
        }
    }

    /**
     * 一次性校验用户名和密码都不为空（登录场景）
     *
     * @param username 用户名
     * @param password 密码
     */
    public void validateUsernameAndPassword(String username, String password) {
        validateUsernameNotBlank(username);
        validatePasswordNotBlank(password);
    }

    /**
     * 一次性校验注册场景：用户名、密码、确认密码都不为空，且两次密码一致
     *
     * @param username        用户名
     * @param password        密码
     * @param confirmPassword 确认密码
     */
    public void validateRegister(String username, String password, String confirmPassword) {
        validateUsernameNotBlank(username);
        validatePasswordNotBlank(password);
        // 确认密码也不能为空
        if (!StringUtils.hasText(confirmPassword)) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }
        validatePasswordConfirm(password, confirmPassword);
    }
}