package com.jacolp.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.annotation.RequireValidRole;
import com.jacolp.component.PasswordEncoder;
import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.AuthenticationException;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.NotFindUserException;
import com.jacolp.exception.PasswordIncorrectException;
import com.jacolp.exception.PermissionDeniedException;
import com.jacolp.exception.UserIsBanException;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.user.UserAddDTO;
import com.jacolp.pojo.dto.user.UserListDTO;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserModifyDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserQuoteStorageDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.provider.UsernameAndPasswordProvider;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.pojo.vo.user.UserOverviewVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.AdminUserService;
import com.jacolp.utils.EmailUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;

    @Override
    public UserEntity loginAdmin(UserLoginDTO userLoginDTO) {
        // 校验用户名和密码非空
        validUsernameAndPassword(userLoginDTO);

        // 通过用户名查询用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 检查账号状态
        if (user.getStatus() == UserConstant.BANNED_STATUS) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }

        // 检查账号权限
        if (user.getRoleId() != RoleConstant.ADMIN
                && user.getRoleId() != RoleConstant.CREATOR) {
            log.error("User isn't admin!");
            throw new PasswordIncorrectException(UserConstant.PERMISSION_DENIED);
        }

        // 检查密码
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        return user;
    }

    @Override
    public PageResult list(UserListDTO userListDTO) {
        // 如果没有传递参数则为查询全表
        if (userListDTO == null) {
            userListDTO = new UserListDTO();
        }

        PageHelper.startPage(userListDTO.getPageNumOrDefault(), userListDTO.getPageSizeOrDefault());
        List<UserEntity> records = userMapper.listByCondition(userListDTO);
        PageInfo<UserEntity> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    // 修改用户时 roleId 可选：未传则不做角色存在性校验，传了才校验
    @RequireValidRole(required = false)
    public void modifyUser(UserModifyDTO dto) {
        log.info("Admin modifying user, target user id: {}", dto.getId());

        // 检查权限
        UserEntity modifier = userMapper.selectById(BaseContext.getCurrentId());
        if (modifier == null) {
            throw new AuthenticationException("操作者用户不存在");
        }
        if (modifier.getRoleId() > RoleConstant.ADMIN) {
            log.error("Permission denied: Modifier roleId={} is not admin/creator", modifier.getRoleId());
            throw new BaseException("权限不足：仅创建者和管理员可以修改账户");
        }
        if (dto.getRoleId() != null && modifier.getRoleId() >= dto.getRoleId()) {
            log.error("Permission denied: Modifier roleId={} cannot assign roleId={}",
                    modifier.getRoleId(), dto.getRoleId());
            throw new BaseException("权限不足：只能创建权限低于自身的账户");
        }

        // 构建更新实体，仅设置非空字段（updateById 的 XML 使用 <if> 动态判断）
        UserEntity user = new UserEntity();
        BeanUtils.copyProperties(dto, user);

        // 处理密码修改：无需旧密码，直接覆盖
        if (StringUtils.hasText(dto.getNewPassword())) {
            // 校验两次输入是否一致
            if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
                throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        // 处理邮箱修改：如果提供了新邮箱，则校验格式
        if (StringUtils.hasText(dto.getEmail())) {
            if (!EmailUtil.isValidEmail(dto.getEmail())) {
                throw new BaseException(UserConstant.INVALID_EMAIL_FORMAT);
            }
        }

        userMapper.updateById(user);
    }

    @Override
    // 新增用户必须指定有效角色
    @RequireValidRole
    public void addUser(UserAddDTO dto) {
        log.info("Admin adding new user, username: {}, roleId: {}", dto.getUsername(), dto.getRoleId());

        // 1. Get the modifier's identity
        Long modifierId = BaseContext.getCurrentId();
        UserEntity modifier = userMapper.selectById(modifierId);
        if (modifier == null) {
            throw new AuthenticationException("操作者用户不存在");
        }

        // 2. Modifier must be admin or creator (roleId <= 2)
        if (modifier.getRoleId() > RoleConstant.ADMIN) {
            log.error("Permission denied: Modifier roleId={} is not admin/creator", modifier.getRoleId());
            throw new PermissionDeniedException("权限不足：仅创建者和管理员可以新增账户");
        }

        // 3. Cannot assign a role equal to or higher than the modifier's own role
        if (dto.getRoleId() == null || dto.getRoleId() <= modifier.getRoleId()) {
            log.error("Permission denied: Modifier roleId={} cannot assign roleId={}",
                    modifier.getRoleId(), dto.getRoleId());
            throw new PermissionDeniedException("权限不足：只能创建权限低于自身的账户");
        }

        // 4. Username and password validation
        validUsernameAndPassword(dto);

        // 5. Email validation
        if (!StringUtils.hasText(dto.getEmail())) {
            throw new BaseException(UserConstant.EMAIL_NOT_PROVIDED);
        }
        if (!EmailUtil.isValidEmail(dto.getEmail())) {
            throw new BaseException(UserConstant.INVALID_EMAIL_FORMAT);
        }

        UserEntity existed = userMapper.selectByUsername(dto.getUsername());
        if (existed != null) {
            throw new BaseException(UserConstant.USER_ALREADY_EXISTS);
        }

        // 5. Build and persist the new user
        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setRoleId(dto.getRoleId());
        Integer status = dto.getStatus();
        user.setStatus(status != null ? status : UserConstant.ACTIVE_STATUS);

        userMapper.insertUser(user);
        log.info("User created successfully, username: {}", dto.getUsername());
    }

    @Override
    public void deleteUsers(List<Long> ids) {
        log.info("Admin deleting users, ids: {}", ids);

        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的用户 ID 列表不能为空");
        }

        // 1. Check modifier identity
        Long modifierId = BaseContext.getCurrentId();
        UserEntity modifier = userMapper.selectById(modifierId);
        if (modifier == null) {
            throw new AuthenticationException("操作者用户不存在");
        }

        // 2. Modifier must be admin or creator (roleId <= 2)
        if (modifier.getRoleId() > RoleConstant.ADMIN) {
            log.error("Permission denied: Modifier roleId={} is not admin/creator", modifier.getRoleId());
            throw new PermissionDeniedException("权限不足：仅创建者和管理员可以删除账户");
        }

        // 3. Fail-fast: pre-fetch all targets and check for any privilege violation
        List<UserEntity> targets = userMapper.selectByIds(ids);
        for (UserEntity target : targets) {
            if (target.getRoleId() <= modifier.getRoleId()) {
                log.error("Permission denied: Cannot delete user id={} (roleId={}), modifier roleId={}",
                        target.getId(), target.getRoleId(), modifier.getRoleId());
                throw new PermissionDeniedException(
                        "权限不足：id=" + target.getId() + " 的用户权限不低于操作者，已终止整批删除");
            }
        }

        // 4. Proceed with batch deletion
        userMapper.deleteByIds(ids);
        log.info("Batch delete completed, count: {}", ids.size());
    }

    @Override
    public void updateStatus(Long targetId, Integer status) {
        log.info("Admin updating user status, target id: {}, new status: {}", targetId, status);

        // Validate status value
        if (status == null || (status != 0 && status != 1)) {
            throw new BaseException("无效的状态值，只接受 0（封禁）或 1（正常）");
        }

        // The AOP @RequireSuperiorRole on the controller already handled role verification.
        // Just build a minimal update entity and persist.
        UserEntity user = new UserEntity();
        user.setId(targetId);
        user.setStatus(status);
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            log.error("User profile update failed, user: {}", user);
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }

        log.info("User status updated successfully, id: {}, status: {}", targetId, status);
    }

    @Override
    public UserEntity getUserById(Long id) {
        if (id == null || id <= 0) {
            log.error("Invalid user id: {}", id);
            throw new BaseException("无效的用户 ID");
        }
        UserEntity user = userMapper.selectById(id);
        user.setPassword(null);
        user.setUpdateTime(null);
        return user;
    }

    @Override
    public UserQuoteStorageDTO getUserQuoteStorage(Long userId) {
        return userMapper.selectQuoteStorageById(userId);
    }

    @Override
    public void updateUserStorageUsed(Long userId, Long usedStorageBytes) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsedStorageBytes(usedStorageBytes);
        userMapper.updateById(user);
    }

    /**
     * 校验用户名和密码
     * @param provider 用户名与密码提供者
     */
    private void validUsernameAndPassword(UsernameAndPasswordProvider provider) {
        if (provider == null) {
            log.error("Invalid username and password provider");
            throw new BaseException(UserConstant.USERNAME_AND_PASSWORD_PROVIDER_ERROR);
        }

        if (!StringUtils.hasText(provider.getUsername())) {
            log.error("Invalid username");
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }

        if (!StringUtils.hasText(provider.getPassword())) {
            log.error("Invalid password");
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }
    }
}
