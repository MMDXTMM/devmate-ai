package com.devmate.project.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectMapperTest {

    @Autowired
    private ProjectMapper projectMapper;

    @Test
    void readsMigratedProjectTable() {
        assertThat(projectMapper.selectCount(null)).isZero();
    }
}
