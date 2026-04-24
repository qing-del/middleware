package com.jacolp.pojo.dto.user;

import com.jacolp.pojo.provider.TargetUserProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封禁/解封账号的请求参数。
 * 实现 {@link TargetUserProvider} 以复用 {@code @RequireSuperiorRole} AOP 权限鉴权切面。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusDTO implements TargetUserProvider {

    /** 目标用户 ID */
    private Long id;

    @Override
    public Long getTargetUserId() {
        return this.id;
    }
}
