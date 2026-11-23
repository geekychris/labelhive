package com.datalabel.security;

import com.datalabel.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleMappingTest {

    private static List<String> names(java.util.Collection<? extends GrantedAuthority> a) {
        return a.stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    @Test void labelerMapsToLabelerAndWorker() {
        List<String> roles = RoleMapping.knownRoles(List.of("labeler"));
        assertThat(roles).containsExactly("labeler");
        assertThat(names(RoleMapping.authorities(roles))).containsExactly("ROLE_LABELER", "ROLE_WORKER");
        assertThat(RoleMapping.appRole(roles)).isEqualTo(Role.WORKER);
    }

    @Test void adminMapsToAdminAndMayLabel() {
        List<String> roles = RoleMapping.knownRoles(List.of("admin"));
        assertThat(names(RoleMapping.authorities(roles))).containsExactly("ROLE_ADMIN", "ROLE_WORKER");
        assertThat(RoleMapping.appRole(roles)).isEqualTo(Role.ADMIN);
    }

    @Test void bothRolesGiveAdmin() {
        List<String> roles = RoleMapping.knownRoles(List.of("labeler", "ADMIN"));
        assertThat(roles).containsExactly("admin", "labeler");
        assertThat(names(RoleMapping.authorities(roles))).containsExactly("ROLE_ADMIN", "ROLE_LABELER", "ROLE_WORKER");
        assertThat(RoleMapping.appRole(roles)).isEqualTo(Role.ADMIN);
    }

    @Test void unknownOrMissingRolesAreIgnored() {
        assertThat(RoleMapping.knownRoles(List.of("requester", "owner"))).isEmpty();
        assertThat(RoleMapping.knownRoles(null)).isEmpty();
        assertThat(RoleMapping.knownRoles(42)).isEmpty();
        assertThat(RoleMapping.appRole(List.of())).isNull();
        assertThat(RoleMapping.authorities(List.of())).isEmpty();
    }

    @Test void aSpaceOrCommaSeparatedStringIsAccepted() {
        assertThat(RoleMapping.knownRoles("labeler admin")).containsExactly("admin", "labeler");
        assertThat(RoleMapping.knownRoles("labeler,x")).containsExactly("labeler");
    }
}
