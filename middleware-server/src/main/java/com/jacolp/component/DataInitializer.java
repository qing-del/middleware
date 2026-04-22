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

        if (creator == null) {
            // 如果不存在则创建一个
            creator = new UserEntity();
            creator.setId(1L);
            creator.setUsername(adminUsername);
            creator.setPassword(passwordEncoder.encode(adminPassword));
            creator.setRoleId(RoleConstant.CREATOR);
            creator.setStatus(UserConstant.DEFAULT_STATUS);
            int count = userMapper.insertCreator(creator);
            if (count <= 0) {
                log.error("Failed to create admin account!");
            }
            log.info("Admin Account was created!");
        } else {
            // 如果存在
            log.warn("The account that userId is 1 already exists!");
            // 对帐号进行数据匹配
            if (!creator.getUsername().equals(adminUsername)) {
                creator.setUsername(adminUsername);
            }
            boolean valid = passwordEncoder.matches(adminPassword, creator.getPassword());
            if (!valid) {
                creator.setPassword(passwordEncoder.encode(adminPassword));
            }
            creator.setRoleId(RoleConstant.CREATOR);
            creator.setStatus(1);

            int count = userMapper.updateById(creator);
            if (count <= 0) {
                log.error("Failed to update admin account!");
            }
        }

        log.warn("The creator account init!");
    }
}
