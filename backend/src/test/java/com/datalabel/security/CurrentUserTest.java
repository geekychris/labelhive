package com.datalabel.security;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Role;
import com.datalabel.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CurrentUserTest {

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void headerIsIgnoredOutsideDebug() {
        AppUserRepository users = mock(AppUserRepository.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "demo");

        assertThat(new CurrentUser(users, false).resolve(req)).isNull();
        verifyNoInteractions(users);
    }

    @Test void headerIsUsedInDebug() {
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser alice = AppUser.builder().id(2L).username("alice").role(Role.WORKER).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "alice");

        assertThat(new CurrentUser(users, true).resolve(req)).isSameAs(alice);
    }
}
