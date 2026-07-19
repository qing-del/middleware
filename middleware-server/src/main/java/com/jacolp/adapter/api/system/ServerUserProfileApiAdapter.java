package com.jacolp.adapter.api.system;

import com.jacolp.mapper.UserMapper;
import com.jacolp.middleware.module.system.api.UserProfileApi;
import com.jacolp.pojo.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transitional implementation backed directly by the legacy user mapper.
 */
@Component
public class ServerUserProfileApiAdapter implements UserProfileApi {

    private final UserMapper userMapper;

    public ServerUserProfileApiAdapter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Map<Long, UserProfile> getProfilesByIds(Collection<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = userIds.stream()
                .peek(id -> {
                    if (id == null || id <= 0) {
                        throw new IllegalArgumentException("userIds must contain positive ids only");
                    }
                })
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, UserProfile> profiles = new LinkedHashMap<>();
        for (UserEntity user : userMapper.selectByIds(ids)) {
            profiles.put(user.getId(), new UserProfile(user.getId(), user.getUsername(), user.getNickname()));
        }
        return Map.copyOf(profiles);
    }
}
