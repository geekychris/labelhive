package com.datalabel.controller;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Role;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private CurrentUser currentUser;
    private AppUserRepository users;
    private MockMvc mvc;

    @BeforeEach void setup() {
        currentUser = mock(CurrentUser.class);
        users = mock(AppUserRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(currentUser, users)).build();
    }

    @Test void meReturnsAuthenticatedFalseWhenNoUser() throws Exception {
        when(currentUser.resolve(any())).thenReturn(null);
        when(currentUser.isDebugMode()).thenReturn(false);

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test void meReturnsUserDetailsWhenAuthenticated() throws Exception {
        when(currentUser.resolve(any())).thenReturn(
                AppUser.builder().id(1L).username("alice").role(Role.WORKER).build());
        when(currentUser.isDebugMode()).thenReturn(true);

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("WORKER"))
                .andExpect(jsonPath("$.debug").value(true));
    }

    @Test void debugUsersReturnsEmptyOutsideDebug() throws Exception {
        when(currentUser.isDebugMode()).thenReturn(false);

        mvc.perform(get("/api/auth/debug-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(users);
    }

    @Test void debugUsersReturnsListInDebug() throws Exception {
        when(currentUser.isDebugMode()).thenReturn(true);
        when(users.findAll()).thenReturn(List.of(
                AppUser.builder().id(1L).username("a").role(Role.ADMIN).build(),
                AppUser.builder().id(2L).username("b").role(Role.WORKER).build()
        ));

        mvc.perform(get("/api/auth/debug-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("a"));
    }
}
