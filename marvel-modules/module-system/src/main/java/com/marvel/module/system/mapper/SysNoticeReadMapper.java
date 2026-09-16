package com.marvel.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marvel.module.system.entity.SysNoticeRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 公告已读 Mapper。 */
@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {

    /** 某用户已读的全部公告 ID */
    @Select("SELECT notice_id FROM sys_notice_read WHERE user_id = #{userId}")
    List<Long> selectReadNoticeIds(@Param("userId") Long userId);

    /** 某用户在一批公告中的已读 ID（用于列表批量标记，避免 N+1） */
    @Select("<script>SELECT notice_id FROM sys_notice_read WHERE user_id = #{userId} AND notice_id IN "
            + "<foreach collection='noticeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectReadNoticeIdsIn(@Param("userId") Long userId, @Param("noticeIds") List<Long> noticeIds);

    /** 未读公告数（仅统计已发布公告） */
    @Select("SELECT COUNT(*) FROM sys_notice n WHERE n.status = '0' AND NOT EXISTS ("
            + "SELECT 1 FROM sys_notice_read r WHERE r.notice_id = n.notice_id AND r.user_id = #{userId})")
    long countUnread(@Param("userId") Long userId);

    /** 某用户未读的公告 ID 列表 */
    @Select("SELECT n.notice_id FROM sys_notice n WHERE n.status = '0' AND NOT EXISTS ("
            + "SELECT 1 FROM sys_notice_read r WHERE r.notice_id = n.notice_id AND r.user_id = #{userId})")
    List<Long> selectUnreadNoticeIds(@Param("userId") Long userId);

    /** 是否已读 */
    @Select("SELECT COUNT(*) FROM sys_notice_read WHERE user_id = #{userId} AND notice_id = #{noticeId}")
    int exists(@Param("userId") Long userId, @Param("noticeId") Long noticeId);
}
