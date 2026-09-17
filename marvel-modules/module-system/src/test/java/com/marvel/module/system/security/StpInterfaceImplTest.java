package com.marvel.module.system.security;

import com.marvel.api.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StpInterfaceImplTest {

    private final SystemApi systemApi = mock(SystemApi.class);
    private final StpInterfaceImpl stpInterface = new StpInterfaceImpl(systemApi);

    @Test
    void getPermissionListDelegatesToSystemApiByUserId() {
        when(systemApi.getPermissionsByUserId(7L)).thenReturn(Set.of("system:user:list"));

        assertThat(stpInterface.getPermissionList("7", "login"))
                .containsExactly("system:user:list");
        verify(systemApi).getPermissionsByUserId(7L);
    }

    @Test
    void getRoleListDelegatesToSystemApiByUserId() {
        when(systemApi.getRoleKeysByUserId(7L)).thenReturn(Set.of("admin", "common"));

        assertThat(stpInterface.getRoleList(7L, "login")).containsExactlyInAnyOrder("admin", "common");
    }

    @Test
    void returnedListsAreMutableCopies() {
        when(systemApi.getPermissionsByUserId(7L)).thenReturn(Set.of("p1"));
        when(systemApi.getRoleKeysByUserId(7L)).thenReturn(Set.of("r1"));

        assertThat(stpInterface.getPermissionList("7", "login")).isInstanceOf(ArrayList.class);
        assertThat(stpInterface.getRoleList("7", "login")).isInstanceOf(ArrayList.class);
    }
}
