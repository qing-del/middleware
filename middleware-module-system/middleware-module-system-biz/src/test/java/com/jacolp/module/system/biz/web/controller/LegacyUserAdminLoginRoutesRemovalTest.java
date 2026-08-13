package com.jacolp.module.system.biz.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyUserAdminLoginRoutesRemovalTest {

    @Test
    void legacyUserAndAdministratorLoginLogoutRoutesAreNotMapped() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new com.jacolp.module.system.biz.web.controller.user.UserController(),
                new com.jacolp.module.system.biz.web.controller.admin.UserController())
                .build();

        mvc.perform(post("/user/user/login")).andExpect(status().isNotFound());
        mvc.perform(post("/user/user/logout")).andExpect(status().isNotFound());
        mvc.perform(post("/admin/user/login")).andExpect(status().isNotFound());
        mvc.perform(post("/admin/user/logout")).andExpect(status().isNotFound());
    }
}
