package com.marvel.module.system.api;

import com.marvel.module.system.entity.SysConfig;
import com.marvel.module.system.service.SysConfigService;
import com.marvel.module.system.service.SysDeptService;
import com.marvel.module.system.service.SysMenuService;
import com.marvel.module.system.service.SysRoleService;
import com.marvel.module.system.service.SysUserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemApiImplTest {

    @Test
    void getConfigValueReturnsValueOrNull() {
        SysConfigService configService = mock(SysConfigService.class);
        SysConfig config = new SysConfig();
        config.setConfigValue("false");
        when(configService.getByKey("sys.captcha.enabled")).thenReturn(config);

        SystemApiImpl api = new SystemApiImpl(
                mock(SysUserService.class), mock(SysRoleService.class),
                mock(SysMenuService.class), mock(SysDeptService.class), configService);

        assertThat(api.getConfigValue("sys.captcha.enabled")).isEqualTo("false");
        assertThat(api.getConfigValue("missing")).isNull();
    }
}
