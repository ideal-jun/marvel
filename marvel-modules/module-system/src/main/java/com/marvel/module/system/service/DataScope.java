package com.marvel.module.system.service;

import java.util.Set;

/**
 * 当前登录用户的数据可见范围（不可变值对象）。
 *
 * @param all        true=可见全部数据
 * @param selfUserId 非空=仅可见本人数据
 * @param deptIds    非空=仅可见这些部门的数据（空集合表示无任何可见范围）
 */
public record DataScope(boolean all, Long selfUserId, Set<Long> deptIds) {

    /** 全部数据（方法名避开记录组件 all() 的取值方法） */
    public static DataScope allData() {
        return new DataScope(true, null, null);
    }

    public static DataScope self(Long userId) {
        return new DataScope(false, userId, null);
    }

    public static DataScope depts(Set<Long> deptIds) {
        return new DataScope(false, null, Set.copyOf(deptIds));
    }
}
