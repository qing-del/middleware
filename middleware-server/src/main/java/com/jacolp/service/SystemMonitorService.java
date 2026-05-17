package com.jacolp.service;

import com.jacolp.pojo.vo.MonitorDataVO;

public interface SystemMonitorService {
    /**
     * 收集服务器的资源信息
     * @return
     */
    MonitorDataVO collect();
}
