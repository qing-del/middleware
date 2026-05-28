package com.jacolp.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.properties.JwtProperties;
import com.jacolp.service.EmailSenderService;
import com.jacolp.pojo.dto.email.EmailSendDTO;
import com.jacolp.pojo.dto.email.EmailResultDTO;
import com.jacolp.constant.UserConstant;
import com.jacolp.utils.JwtUtil;
import com.jacolp.utils.KeyToolUtil;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;

@Service
@Slf4j
public class EmailSenderServiceImpl implements EmailSenderService {
    @Autowired private JavaMailSender mailSender;
    @Autowired private TemplateEngine templateEngine;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private UserMapper userMapper;
    @Autowired private StringRedisTemplate redis;

    @Value("${jacolp.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public String sendActivationEmail(UserEntity user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.ACTIVE_SIGN_KEY, true);
        claims.put(UserConstant.USER_ID_CLAIM, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getActiveSecretKey(),
                jwtProperties.getActiveTtl(),
                claims);

        String activationUrl = baseUrl + "/user/user/active/" + token;

        // 生成 6 位数字激活码并存入 Redis
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        redis.opsForValue().set(
                KeyToolUtil.getActiveCodeKey(code),
                String.valueOf(user.getId()),
                Duration.ofMillis(jwtProperties.getActiveTtl()));
        log.info("Activation code generated for user: {}", user.getId());

        Context ctx = new Context();
        ctx.setVariable("username", user.getUsername());
        ctx.setVariable("activationUrl", activationUrl);
        ctx.setVariable("expiryMinutes", jwtProperties.getActiveTtl() / 60000);
        ctx.setVariable("activationCode", code);
        String html = templateEngine.process("email/activation", ctx);

        sendHtmlMail(user.getEmail(), "CoreNode 账号激活", html);
        log.info("Activation email sent to: {}", user.getEmail());
        return token;
    }

    @Override
    public EmailResultDTO sendCustomEmail(EmailSendDTO dto) {
        List<String> recipients = new ArrayList<>();

        if (dto.getUserId() != null) {
            UserEntity user = userMapper.selectById(dto.getUserId());
            if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
                recipients.add(user.getEmail());
            }
        }

        if (dto.getRoleId() != null) {
            List<UserEntity> users = userMapper.selectByRoleId(dto.getRoleId());
            for (UserEntity u : users) {
                if (u.getEmail() != null && !u.getEmail().isEmpty()
                        && !recipients.contains(u.getEmail())) {
                    recipients.add(u.getEmail());
                }
            }
        }

        if (recipients.isEmpty()) {
            return new EmailResultDTO(0, 0, "无有效收件人");
        }

        int success = 0, fail = 0;
        for (String to : recipients) {
            try {
                if (dto.getTemplateName() != null && !dto.getTemplateName().isEmpty()) {
                    Context ctx = new Context();
                    ctx.setVariable("subject", dto.getSubject());
                    ctx.setVariable("body", dto.getBody());
                    String html = templateEngine.process("email/" + dto.getTemplateName(), ctx);
                    sendHtmlMail(to, dto.getSubject(), html);
                } else {
                    sendHtmlMail(to, dto.getSubject(), dto.getBody());
                }
                success++;
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", to, e.getMessage());
                fail++;
            }
        }

        return new EmailResultDTO(success, fail,
                String.format("发送完成：成功 %d，失败 %d", success, fail));
    }

    @Override
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendEmailChangeCode(UserEntity user, String newEmail) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        redis.opsForValue().set(
                KeyToolUtil.getEmailChangeCodeKey(code),
                user.getId() + "|" + newEmail,
                Duration.ofMillis(jwtProperties.getActiveTtl()));
        log.info("Email change code generated for user: {}, new email: {}", user.getId(), newEmail);

        Context ctx = new Context();
        ctx.setVariable("username", user.getUsername());
        ctx.setVariable("newEmail", newEmail);
        ctx.setVariable("verificationCode", code);
        ctx.setVariable("expiryMinutes", jwtProperties.getActiveTtl() / 60000);
        String html = templateEngine.process("email/email-change", ctx);

        sendHtmlMail(newEmail, "CoreNode 邮箱修改验证", html);
        log.info("Email change code sent to: {}", newEmail);
    }
}
