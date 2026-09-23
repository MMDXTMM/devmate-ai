package com.devmate.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmate.conversation.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
    @Select("SELECT * FROM conversation WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Conversation selectByIdForUpdate(@Param("id") Long id);
}

