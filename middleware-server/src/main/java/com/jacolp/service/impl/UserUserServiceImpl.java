package com.jacolp.service.impl;

import com.jacolp.component.PasswordEncoder;
import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.NotFindUserException;
import com.jacolp.exception.PasswordIncorrectException;
import com.jacolp.exception.UserIsBanException;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.pojo.vo.user.UserOverviewVO;
import com.jacolp.properties.JwtProperties;
import com.jacolp.service.UserUserService;
import com.jacolp.utils.EmailUtil;
import com.jacolp.utils.JwtUtil;
import com.jacolp.utils.KeyToolUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Validated
public class UserUserServiceImpl implements UserUserService {
    @Autowired private UserMapper userMapper;

    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public String loginUser(@NotNull @Valid UserLoginDTO userLoginDTO) {
        // 1. 根据用户名查用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 2.1 账号被禁用则直接拒绝登录
        if (user.getStatus() == UserConstant.BANNED_STATUS) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }

        // TODO 加入用户账号是否激活检测

        // 3. 校验密码是否一致
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        // 将用户ID写入 JWT，后续请求会通过拦截器解析出来
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.USER_ID_CLAIM, user.getId());
        String jwt = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        // 将 jwt 存入 Redis
        redis.opsForValue().set(KeyToolUtil.getUserLoginKey(user.getId()), jwt);

        return jwt;
    }

    @Override
    public void logout() {
        redis.delete(KeyToolUtil.getUserLoginKey(BaseContext.getCurrentId()));
    }

    @Override
    public String register(@NotNull @Valid UserRegisterDTO userRegisterDTO) {
        // 校验两次密码一致性
        if (!userRegisterDTO.getPassword().equals(userRegisterDTO.getConfirmPassword())) {
            throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
        }

        // 检查是否存在相同用户名的用户
        UserEntity existed = userMapper.selectByUsername(userRegisterDTO.getUsername());
        if (existed != null) {
            throw new BaseException(UserConstant.USER_ALREADY_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(userRegisterDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setNickname(userRegisterDTO.getUsername());    // 默认昵称为用户名
        user.setRoleId(RoleConstant.USER);
        user.setStatus(UserConstant.UNACTIVE_STATUS);   // 默认用户状态为未激活
        user.setMaxStorageBytes(RoleConstant.USER_MAX_STORAGE_BYTES);   // 默认用户存储空间为 100MB

        int count = userMapper.insertUser(user);
        if (count <= 0) {
            throw new BaseException("注册失败");
        }
        return "注册成功";
    }

    /**
     * 获取当前登录用户详情（不含密码）
     * @return 当前登录用户详情
     */
    @Override
    public UserDetailVO getCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 转换为 VO，排除密码等敏感字段
        UserDetailVO vo = new UserDetailVO();
        BeanUtils.copyProperties(user, vo);
        vo.setId(null);
        if (vo.getNickname() == null || vo.getNickname().isEmpty()) {
            vo.setNickname(vo.getUsername());
        }
        return vo;
    }

    /**
     * 获取当前登录用户概览信息（不含用户ID）。
     */
    @Override
    public UserOverviewVO getUserOverview() {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        UserOverviewVO vo = new UserOverviewVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void deleteCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 软删除：仅更新状态为已删除，保留历史数据
        UserEntity updateEntity = new UserEntity();
        updateEntity.setId(userId);
        updateEntity.setStatus(UserConstant.DELETED_STATUS);
        int affected = userMapper.updateById(updateEntity);
        if (affected <= 0) {
            log.error("User soft-delete failed, userId: {}", userId);
            throw new BaseException("用户删除失败");
        }

        log.info("User soft-deleted (account deactivated), userId: {}", userId);
    }


    @Override
    public void updateCurrentUserProfile(@NotNull @Valid UserProfileUpdateDTO dto) {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检验是否需要更改名称
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        // 检验是否需要更改邮箱
        if (StringUtils.hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail());
        }

        // 检验是否需要更改密码
        if (StringUtils.hasText(dto.getPassword())) {
            if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                throw new BaseException(UserConstant.USER_PASSWORD_ERROR);
            }
            if (StringUtils.hasText(dto.getConfirmPassword())
                    || !dto.getNewPassword().equals(dto.getConfirmPassword())) {
                throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        // 更新数据库
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            log.error("User profile update failed, userId: {}", userId);
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        log.info("User profile updated, userId: {}", userId);
    }

    /**
     * 用户激活
     * @param userId 激活码
     * @return 激活结果
     */
    @Override
    public String activeAccount(Long userId) {
        log.info("User active: {}", userId);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            log.error("User not found, userId: {}", userId);
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检查是否出现重复激活
        if (user.getStatus() != UserConstant.UNACTIVE_STATUS) {
            log.error("User status is active, not reconditioning, userId: {}", user.getId());
            throw new BaseException(UserConstant.USER_ALREADY_ACTIVE);
        }

        // 更新数据库
        user.setStatus(UserConstant.ACTIVE_STATUS);
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            log.error("User active failed, userId: {}", user.getId());
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        return "激活成功";
    }

    /**
     * 用于获取用户激活码
     * @param userId 用户ID
     */
    @Override
    public String getActiveAccountToken(Long userId) {
        UserEntity user = userMapper.selectById(userId);

        // 检查用户是否存在
        if (user == null) {
            log.error("User not found, userId: {}", userId);
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检查账号是否处于未激活状态
        if (user.getStatus() != UserConstant.UNACTIVE_STATUS) {
            throw new BaseException(UserConstant.USER_ALREADY_ACTIVE);
        }

        // 检查是否存在邮箱
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            log.error("User email is empty, userId: {}", userId);
            throw new BaseException(UserConstant.USER_EMAIL_IS_EMPTY);
        }

        // 校验邮箱格式
        if (!EmailUtil.isValidEmail(user.getEmail())) {
            log.error("Invalid email format, userId: {}, email: {}", userId, user.getEmail());
            throw new BaseException(UserConstant.INVALID_EMAIL_FORMAT);
        }

        // 生成 JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.ACTIVE_SIGN_KEY, true);
        claims.put(UserConstant.USER_ID_CLAIM, userId);

        return JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
    }
}
