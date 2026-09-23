package com.devmate.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmate.conversation.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> { }

