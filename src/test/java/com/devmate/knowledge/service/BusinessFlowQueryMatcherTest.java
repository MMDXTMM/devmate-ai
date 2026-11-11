package com.devmate.knowledge.service;

import com.devmate.knowledge.dto.ProjectUnderstandingFlowResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessFlowQueryMatcherTest {
    private final ProjectUnderstandingFlowResponse ticket = flow("维修工单验收", "POST /tickets/accept");
    private final ProjectUnderstandingFlowResponse inventory = flow("库存盘点调整", "POST /inventory/adjust");
    private final List<ProjectUnderstandingFlowResponse> flows = List.of(ticket, inventory);

    @Test
    void mapsChineseBusinessQuestionsUsingThisProjectsOwnNames() {
        assertThat(BusinessFlowQueryMatcher.match("维修工单验收时会发生什么？", flows)).containsExactly(ticket);
        assertThat(BusinessFlowQueryMatcher.match("请讲解库存盘点的实现流程", flows)).containsExactly(inventory);
        assertThat(BusinessFlowQueryMatcher.match("inventory adjust", flows)).containsExactly(inventory);
    }

    @Test
    void doesNotMatchOnlyGenericWordsOrUnrelatedBusiness() {
        assertThat(BusinessFlowQueryMatcher.match("代码实现流程", flows)).isEmpty();
        assertThat(BusinessFlowQueryMatcher.match("优惠券下单", flows)).isEmpty();
        assertThat(BusinessFlowQueryMatcher.match("这个项目有哪些功能？", flows)).containsExactlyElementsOf(flows);
    }

    private ProjectUnderstandingFlowResponse flow(String name, String api) {
        return new ProjectUnderstandingFlowResponse(name, "", List.of(), List.of(api), List.of(), List.of());
    }
}
