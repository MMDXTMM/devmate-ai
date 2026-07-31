package com.devmate.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmate.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}

