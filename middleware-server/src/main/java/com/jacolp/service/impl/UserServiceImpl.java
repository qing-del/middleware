package com.jacolp.service.impl;

import com.jacolp.annotation.RequireValidRole;
import com.jacolp.constant.PageConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.constant.RoleConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.*;
import com.jacolp.utils.PasswordEncoder;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.UserAddDTO;
import com.jacolp.pojo.dto.UserListDTO;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.dto.UserModifyDTO;
import com.jacolp.pojo.dto.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.result.PageResult;
import com.jacolp.service.UserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;

    @Override
    public UserEntity loginAdmin(UserLoginDTO userLoginDTO) {
        // 通过用户名查询用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 检查账号状态
        if (user.getStatus() == 0) {
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
    public UserEntity loginUser(UserLoginDTO userLoginDTO) {
        // 检查用户名是否为空
        if (!StringUtils.hasText(userLoginDTO.getUsername())) {
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }
        
        // 检查密码是否为空，避免后续密码比对出现无意义调用
        if (!StringUtils.hasText(userLoginDTO.getPassword())) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }

        // 1. 根据用户名查用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 2. 账号被禁用则直接拒绝登录
        if (user.getStatus() == 0) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }


        // 3. 校验密码是否一致
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        return user;
    }

    @Override
    public String register(UserRegisterDTO userRegisterDTO) {
        // 检查用户名是否为空
        if (!StringUtils.hasText(userRegisterDTO.getUsername())) {
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }

        // 检查密码是否为空
        if (!StringUtils.hasText(userRegisterDTO.getPassword()) || !StringUtils.hasText(userRegisterDTO.getConfirmPassword())) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }

        // 检查两次密码是否一致
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
        user.setStatus(UserConstant.DEFAULT_STATUS);

        int count = userMapper.insertUser(user);
        if (count <= 0) {
            throw new BaseException("注册失败");
        }
        return "注册成功";
    }

    @Override
    public PageResult list(UserListDTO userListDTO) {
        // 如果没有传递参数则为查询全表
        if (userListDTO == null) {
            userListDTO = new UserListDTO();
        }

        // 如果没有传递分页参数，则默认为第一页
        Integer pageParam = userListDTO.getPage();
        Integer pageSizeParam = userListDTO.getPageSize();
        int page = pageParam == null || pageParam <= 0 ? PageConstant.DEFAULT_PAGE : pageParam;
        int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? PageConstant.DEFAULT_PAGE_SIZE : pageSizeParam;
        PageHelper.startPage(page, pageSize);


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
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setRoleId(dto.getRoleId());
        user.setStatus(dto.getStatus());

        // 处理密码修改：无需旧密码，直接覆盖
        if (StringUtils.hasText(dto.getNewPassword())) {
            // 校验两次输入是否一致
            if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
                throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
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

        // 4. Username uniqueness check
        if (!StringUtils.hasText(dto.getUsername())) {
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
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
        user.setStatus(status != null ? status : UserConstant.DEFAULT_STATUS);

        userMapper.insertUser(user);
        log.info("User created successfully, username: {}", dto.getUsername());
    }

    @Override
    //@Transactional    因为只有一次操作，并且在此之前已经完成了权限校验，不需要事务
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
        userMapper.updateById(user);

        log.info("User status updated successfully, id: {}, status: {}", targetId, status);
    }
}
