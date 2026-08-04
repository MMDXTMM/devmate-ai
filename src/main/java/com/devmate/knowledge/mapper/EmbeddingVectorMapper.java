package com.devmate.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmate.knowledge.entity.EmbeddingVector;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmbeddingVectorMapper extends BaseMapper<EmbeddingVector> {
}
