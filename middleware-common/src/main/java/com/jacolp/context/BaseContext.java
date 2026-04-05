package com.jacolp.context;

import com.jacolp.exception.AuthenticationException;

public class BaseContext {
    private static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    public static void setCurrentId(Long id){
        threadLocal.set(id);
    }

    /**
     * 获取当前登录用户ID
     * @throws AuthenticationException 检测到 id=null 时抛出
     * @return 当前登录用户ID
     */
    public static Long getCurrentId(){
        if(threadLocal.get() == null){
            throw new AuthenticationException("当前登录信息已失效");
        }
        return threadLocal.get();
    }
    public static void remove(){
        threadLocal.remove();
    }
}
