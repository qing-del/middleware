package com.jacolp.service.impl;

import com.jacolp.annotation.RequireValidRole;
import com.jacolp.constant.PageConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.constant.RoleConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.*;
import com.jacolp.component.PasswordEncoder;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.user.UserAddDTO;
import com.jacolp.pojo.dto.user.UserListDTO;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserModifyDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.provider.UsernameAndPasswordProvider;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.UserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
    public UserEntity loginUser(UserLoginDTO userLoginDTO) {
        // 校验用户名和密码非空
        validUsernameAndPassword(userLoginDTO);

        // 1. 根据用户名查用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 2. 账号被禁用则直接拒绝登录
        if (user.getStatus() == UserConstant.BANNED_STATUS) {
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
        // 校验用户名、密码非空及两次密码一致性
        validUsernameAndPassword(userRegisterDTO);
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
        BeanUtils.copyProperties(dto, user);

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

        // 4. Username and password validation
        validUsernameAndPassword(dto);

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

    /**
     * 获取当前登录用户详情（不含密码）
     * @return 当前登录用户详情
     */
    @Override
    public UserDetailVO getCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 转换为 VO，排除密码等敏感字段
        UserDetailVO vo = new UserDetailVO();
        BeanUtils.copyProperties(user, vo);
        vo.setId(null);
        return vo;
    }

    @Override
    public void updateCurrentUserProfile(UserProfileUpdateDTO dto) {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 构建更新实体，仅设置非空字段
        UserEntity updateEntity = new UserEntity();
        updateEntity.setId(userId);

        if (StringUtils.hasText(dto.getNickname())) {
            updateEntity.setNickname(dto.getNickname());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            updateEntity.setEmail(dto.getEmail());
        }

        int affected = userMapper.updateById(updateEntity);
        if (affected <= 0) {
            log.error("User profile update failed, userId: {}", userId);
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        log.info("User profile updated, userId: {}", userId);
    }

    @Override
    public void deleteCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
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
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
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
     * 检查账户是否激活
     * <p>此接口用于检查是否放行发放用户激活码的</p>
     * @param userId 用户ID
     * @return 放行获取激活码返回 true，否则返回 false
     */
    @Override
    public boolean checkActivationStatus(Long userId) {
        UserEntity user = userMapper.selectById(userId);

        // 检查用户是否存在
        if (user == null) {
            log.error("User not found, userId: {}", userId);
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 检查是否存在邮箱
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            log.error("User email is empty, userId: {}", userId);
            throw new BaseException(UserConstant.USER_EMAIL_IS_EMPTY);
        }
        // TODO 后续可以加入一个邮箱正则表达式检查

        return user.getStatus() == UserConstant.UNACTIVE_STATUS;
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
