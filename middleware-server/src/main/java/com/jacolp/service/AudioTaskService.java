package com.jacolp.service;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.pojo.dto.audio.AudioCallbackFinishDTO;
import com.jacolp.pojo.dto.audio.AudioCallbackStartDTO;
import com.jacolp.pojo.dto.audio.AudioTaskPageQueryDTO;
import com.jacolp.pojo.dto.audio.AudioTaskSubmitDTO;
import com.jacolp.pojo.entity.AudioTaskEntity;
import com.jacolp.pojo.vo.audio.AudioTaskSubmitVO;
import com.jacolp.pojo.vo.audio.AudioTaskVO;
import com.jacolp.result.PageResult;

public interface AudioTaskService {

    /**
     * 提交音频任务
     * @param dto
     * @return
     */
    AudioTaskSubmitVO submitTask(AudioTaskSubmitDTO dto);

    /**
     * 任务开始回调
     * <p>- 会去数据库将任务状态更新为处理中</p>
     * @param dto
     * @return
     */
    boolean callbackStart(AudioCallbackStartDTO dto);

    /**
     * 任务结束回调
     * <p>- 会去数据库将任务状态更新为完成</p>
     * <p>- 同时将资源的 url 放到 DB 中</p>
     * @param dto
     * @return
     */
    boolean callbackFinish(AudioCallbackFinishDTO dto);

    /**
     * 获取任务详情
     * @param taskId
     * @return
     */
    AudioTaskVO getTask(Long taskId);

    /**
     * 获取任务列表
     * <p>- 使用 {@link PermissionContext#isAdmin()} 做管理员校验</p>
     * <p>- 如果不是管理员，会使用 {@link BaseContext#getCurrentId()} 来做资源重定向</p>
     */
    PageResult listTasks(AudioTaskPageQueryDTO dto);

    /**
     * 重新加入任务队列
     * @param task
     */
    void requeueTask(AudioTaskEntity task);
}