package com.jacolp.adapter.api.system;

import com.jacolp.mapper.ApiDailyUsageMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.middleware.module.system.api.UserProfileApi;
import com.jacolp.middleware.module.system.api.quota.ConsumeQuotaCommand;
import com.jacolp.middleware.module.system.api.quota.ConsumeQuotaResult;
import com.jacolp.pojo.entity.ApiDailyUsageEntity;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.utils.RoleDataComputerUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerApiAdapterTest {

    @Test
    void userProfilesAreMappedFromOneBatchMapperCall() {
        UserMapper userMapper = mock(UserMapper.class);
        UserEntity user = new UserEntity();
        user.setId(2L);
        user.setUsername("alice");
        user.setNickname("Alice");
        when(userMapper.selectByIds(List.of(2L, 1L))).thenReturn(List.of(user));

        Map<Long, UserProfileApi.UserProfile> profiles =
                new ServerUserProfileApiAdapter(userMapper).getProfilesByIds(List.of(2L, 1L, 2L));

        assertEquals(new UserProfileApi.UserProfile(2L, "alice", "Alice"), profiles.get(2L));
        verify(userMapper).selectByIds(List.of(2L, 1L));
    }

    @Test
    void dailyApiConsumptionUsesTheCommandDateAndAmount() {
        UserMapper userMapper = mock(UserMapper.class);
        ApiDailyUsageMapper usageMapper = mock(ApiDailyUsageMapper.class);
        UserEntity user = new UserEntity();
        user.setRoleId(3L);
        RoleDataComputerUtil.putApiLimit(3L, 10);
        when(userMapper.selectById(7L)).thenReturn(user);

        ApiDailyUsageEntity before = new ApiDailyUsageEntity();
        before.setUsedCount(1);
        ApiDailyUsageEntity after = new ApiDailyUsageEntity();
        after.setUsedCount(3);
        LocalDate date = LocalDate.of(2026, 7, 19);
        when(usageMapper.selectByUserIdAndDate(7L, date)).thenReturn(before, after);
        when(usageMapper.incrementUsageBy(7L, date, 2L)).thenReturn(1);

        ConsumeQuotaResult result = new ServerUserQuotaApiAdapter(userMapper, usageMapper)
                .consume(ConsumeQuotaCommand.dailyApiCall(7L, 2L, date));

        assertTrue(result.consumed());
        assertEquals(3L, result.quota().used());
        verify(usageMapper).incrementUsageBy(7L, date, 2L);
        verify(usageMapper, times(2)).selectByUserIdAndDate(eq(7L), eq(date));
    }
}
