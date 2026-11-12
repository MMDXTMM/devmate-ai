package com.devmate.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmate.knowledge.entity.ProjectUnderstandingReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ProjectUnderstandingReportMapper extends BaseMapper<ProjectUnderstandingReport> {
    @Select("""
            <script>
            SELECT r.project_id, r.report_json FROM project_understanding_report r
            JOIN project p ON p.id = r.project_id AND p.deleted = 0 AND p.current_revision = r.revision
            WHERE r.user_id = #{userId} AND r.status = 'SUCCEEDED'
              AND r.project_id IN
              <foreach collection="projectIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              AND EXISTS (SELECT 1 FROM project_member m WHERE m.project_id = r.project_id AND m.user_id = #{userId})
              AND NOT EXISTS (
                SELECT 1 FROM project_understanding_report newer
                WHERE newer.user_id = r.user_id AND newer.project_id = r.project_id
                  AND newer.revision = r.revision AND newer.status = 'SUCCEEDED'
                  AND (newer.created_at &gt; r.created_at OR (newer.created_at = r.created_at AND newer.id &gt; r.id))
              )
            </script>
            """)
    List<ProjectUnderstandingReport> latestVisibleIconReports(@Param("userId") Long userId,
                                                             @Param("projectIds") List<Long> projectIds);
}
