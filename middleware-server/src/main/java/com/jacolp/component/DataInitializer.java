package com.jacolp.component;

import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
/**
 * 数据初始化
 * 强制 创建/修改 出一个 Creator 角色的账号
 * 固定改动 id 为 1 的账号
 */
public class DataInitializer implements CommandLineRunner {
    @Value("${jacolp.admin.username}")
    private String adminUsername;

    @Value("${jacolp.admin.password}")
    private String adminPassword;

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否存在管理员账号
        UserEntity creator = userMapper.selectById(1L);
        if (creator == null) {
            creator = userMapper.selectByUsername(adminUsername);
        }

        // 强制加入一个创建者角色
        creator = new UserEntity();
        creator.setId(1L);
        creator.setUsername(adminUsername);
        creator.setPassword(passwordEncoder.encode(adminPassword));
        creator.setRoleId(RoleConstant.CREATOR);
        creator.setStatus(UserConstant.ACTIVE_STATUS);
        int count = userMapper.upsertCreator(creator);
        if (count <= 0) {
            log.error("Failed to create admin account!");
            throw new RuntimeException("Failed to create admin account!");
        }

        log.warn("The creator account init!");
    }
}
