package com.devmate.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmate.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("SELECT * FROM project WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Project selectByIdForUpdate(@Param("id") Long id);
}
