package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marvel.api.system.dto.MenuDTO;
import com.marvel.module.system.entity.SysMenu;
import com.marvel.module.system.mapper.SysMenuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysMenuServiceImplTest {

    /** LambdaQueryWrapper 解析列名依赖 MyBatis-Plus 的 TableInfo 缓存，纯单测需手动初始化 */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysMenu.class);
    }

    private SysMenuServiceImpl serviceWith(SysMenuMapper mapper) {
        SysMenuServiceImpl service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private SysMenu menu(long id, long parentId, String type) {
        SysMenu m = new SysMenu();
        m.setMenuId(id);
        m.setParentId(parentId);
        m.setMenuType(type);
        m.setMenuName("m" + id);
        m.setOrderNum((int) id);
        return m;
    }

    @Test
    void childMenuIdsTraverseAllDescendantsInSingleQuery() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                menu(1, 0, "M"), menu(2, 1, "M"), menu(3, 1, "C"), menu(4, 2, "C"), menu(9, 0, "M")));

        List<Long> ids = serviceWith(mapper).getChildMenuIds(1L);

        assertThat(ids).containsExactlyInAnyOrder(2L, 3L, 4L);
        verify(mapper, times(1)).selectList(any());
    }

    @Test
    void menuTreeExcludesButtonsAndGroupsByParent() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                menu(1, 0, "M"), menu(2, 1, "C"), menu(3, 1, "F"), menu(4, 0, "C")));

        List<MenuDTO> tree = serviceWith(mapper).getMenuTree(1L, true);

        assertThat(tree).extracting(MenuDTO::getId).containsExactlyInAnyOrder(1L, 4L);
        MenuDTO root1 = tree.stream().filter(t -> t.getId().equals(1L)).findFirst().orElseThrow();
        assertThat(root1.getChildren()).extracting(MenuDTO::getId).containsExactly(2L);
    }
}
