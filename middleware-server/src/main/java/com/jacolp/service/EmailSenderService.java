package com.jacolp.service;

import com.jacolp.pojo.dto.email.EmailSendDTO;
import com.jacolp.pojo.dto.email.EmailResultDTO;
import com.jacolp.pojo.entity.UserEntity;

public interface EmailSenderService {
    /** 发送激活邮件，返回生成的 token 供调试 */
    String sendActivationEmail(UserEntity user);

    /** 管理员发送自定义邮件 */
    EmailResultDTO sendCustomEmail(EmailSendDTO dto);

    /** 底层发送 HTML 邮件 */
    void sendHtmlMail(String to, String subject, String htmlContent);
}
