package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.framework.config.CacheConfig;
import com.marvel.module.system.entity.SysMenu;
import com.marvel.module.system.service.SysMenuService;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理接口，路径前缀 /system/**（与未来网关路由一致）。
 * 菜单变更仅影响后续登录会话的路由与权限（Sa-Token 注解实时查库，无需重启）。
 */
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    /** 全量菜单列表（平铺，角色菜单树等需要自行组树的场景使用） */
    @SaCheckPermission("system:menu:list")
    @GetMapping("/list")
    public R<List<SysMenu>> list(@RequestParam(required = false) String menuName,
                                 @RequestParam(required = false) String status) {
        return R.ok(menuService.listMenus(menuName, status));
    }

    /**
     * 菜单树（懒加载）：
     * - 带 menuName：返回完整树（保留命中节点祖先链），供搜索场景一次展示；
     * - 不带 menuName：parentId 为空返回根节点，否则返回其直接子节点，
     *   每个节点带 hasChild 标记，前端按需展开加载，避免全量拉取。
     */
    @SaCheckPermission("system:menu:list")
    @GetMapping("/tree")
    public R<List<SysMenu>> tree(@RequestParam(required = false) Long parentId,
                                 @RequestParam(required = false) String menuName,
                                 @RequestParam(required = false) String status) {
        if (StringUtils.hasText(menuName)) {
            return R.ok(menuService.listTree(menuName, status));
        }
        return R.ok(menuService.listLevel(parentId, status));
    }

    @SaCheckPermission("system:menu:query")
    @GetMapping("/{menuId}")
    public R<SysMenu> detail(@PathVariable Long menuId) {
        return R.ok(menuService.getById(menuId));
    }

    @SaCheckPermission("system:menu:add")
    @Log(title = "菜单管理", businessType = Log.BusinessType.INSERT)
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_PERMS, allEntries = true)
    @PostMapping
    public R<Void> add(@RequestBody SysMenu menu) {
        menu.setMenuId(null);
        menuService.save(menu);
        return R.ok();
    }

    @SaCheckPermission("system:menu:edit")
    @Log(title = "菜单管理", businessType = Log.BusinessType.UPDATE)
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_PERMS, allEntries = true)
    @PutMapping
    public R<Void> update(@RequestBody SysMenu menu) {
        // 防止把上级设为自身或自己的子孙，形成环导致树遍历无限递归（DoS）
        if (menu.getMenuId() != null && menu.getMenuId().equals(menu.getParentId())) {
            return R.fail("上级菜单不能为自身");
        }
        if (menu.getMenuId() != null && menu.getParentId() != null
                && menuService.getChildMenuIds(menu.getMenuId()).contains(menu.getParentId())) {
            return R.fail("上级菜单不能为自身的子菜单");
        }
        menuService.updateById(menu);
        return R.ok();
    }

    @SaCheckPermission("system:menu:remove")
    @Log(title = "菜单管理", businessType = Log.BusinessType.DELETE)
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_PERMS, allEntries = true)
    @DeleteMapping("/{menuId}")
    public R<Void> remove(@PathVariable Long menuId) {
        List<Long> childIds = menuService.getChildMenuIds(menuId);
        if (!childIds.isEmpty()) {
            return R.fail("存在子菜单，不允许删除");
        }
        menuService.removeById(menuId);
        return R.ok();
    }
}
