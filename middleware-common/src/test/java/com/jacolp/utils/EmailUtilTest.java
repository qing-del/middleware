package com.jacolp.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EmailUtilTest {

    @Test
    public void testValidEmails() {
        // 测试有效的邮箱地址
        assertTrue(EmailUtil.isValidEmail("test@example.com"));
        assertTrue(EmailUtil.isValidEmail("user.name+tag@domain.co.uk"));
        assertTrue(EmailUtil.isValidEmail("user123@test-domain.org"));
        assertTrue(EmailUtil.isValidEmail("admin@company.com"));
    }

    @Test
    public void testInvalidEmails() {
        // 测试无效的邮箱地址
        assertFalse(EmailUtil.isValidEmail(null));
        assertFalse(EmailUtil.isValidEmail(""));
        assertFalse(EmailUtil.isValidEmail("invalid-email"));
        assertFalse(EmailUtil.isValidEmail("@example.com"));
        assertFalse(EmailUtil.isValidEmail("user@"));
        assertFalse(EmailUtil.isValidEmail("user@.com"));
        assertFalse(EmailUtil.isValidEmail("user@example"));
    }

    @Test
    public void testSupportedEmails() {
        // 测试支持的邮箱域名
        assertTrue(EmailUtil.isSupportedEmail("test@gmail.com"));
        assertTrue(EmailUtil.isSupportedEmail("user@qq.com"));
        assertTrue(EmailUtil.isSupportedEmail("admin@163.com"));
        assertTrue(EmailUtil.isSupportedEmail("contact@outlook.com"));
    }

    @Test
    public void testUnsupportedEmails() {
        // 测试不支持的邮箱域名
        assertFalse(EmailUtil.isSupportedEmail("test@unknown-domain.com"));
        assertFalse(EmailUtil.isSupportedEmail("user@fake-email.net"));
    }

    @Test
    public void testExtractDomain() {
        // 测试域名提取功能
        assertEquals("gmail.com", EmailUtil.extractDomain("test@gmail.com"));
        assertEquals("qq.com", EmailUtil.extractDomain("user@qq.com"));
        assertEquals("example.org", EmailUtil.extractDomain("admin@example.org"));
        assertNull(EmailUtil.extractDomain(null));
        assertNull(EmailUtil.extractDomain("invalid-email"));
    }

    @Test
    public void testGetSupportedDomains() {
        // 测试获取支持的域名列表
        assertNotNull(EmailUtil.getSupportedDomains());
        assertTrue(EmailUtil.getSupportedDomains().size() > 0);
        assertTrue(EmailUtil.getSupportedDomains().contains("gmail.com"));
        assertTrue(EmailUtil.getSupportedDomains().contains("qq.com"));
    }

    @Test
    public void testAddRemoveSupportedDomain() {
        // 测试添加和移除支持的域名
        int initialSize = EmailUtil.getSupportedDomains().size();
        
        EmailUtil.addSupportedDomain("newdomain.com");
        assertEquals(initialSize + 1, EmailUtil.getSupportedDomains().size());
        assertTrue(EmailUtil.getSupportedDomains().contains("newdomain.com"));
        
        EmailUtil.removeSupportedDomain("newdomain.com");
        assertEquals(initialSize, EmailUtil.getSupportedDomains().size());
        assertFalse(EmailUtil.getSupportedDomains().contains("newdomain.com"));
    }
}
