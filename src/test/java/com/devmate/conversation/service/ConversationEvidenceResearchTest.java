package com.devmate.conversation.service;

import com.devmate.agent.config.ProjectConversationProperties;
import com.devmate.agent.model.*;
import com.devmate.knowledge.dto.*;
import com.devmate.knowledge.entity.*;
import com.devmate.knowledge.mapper.*;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.user.service.ProjectAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.List;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationEvidenceResearchTest {
    final KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
    final KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
    final ContextRetrievalService retrieval = mock(ContextRetrievalService.class);
    final ProjectAccessService access = mock(ProjectAccessService.class);
    final ProjectConversationProperties properties = new ProjectConversationProperties();
    final ProjectConversationModel model = mock(ProjectConversationModel.class);
    ConversationEvidenceResearch research;
    KnowledgeChunk chunk;
    @BeforeEach void setup() {
        research = new ConversationEvidenceResearch(chunks,documents,retrieval,access,properties,new ObjectMapper());
        var doc = new KnowledgeDocument(); doc.setId(2L);doc.setProjectId(1L);doc.setRevision("abc");doc.setFilePath("Order.java");
        when(documents.selectById(2L)).thenReturn(doc);
        chunk=new KnowledgeChunk();chunk.setId(3L);chunk.setDocumentId(2L);chunk.setProjectId(1L);chunk.setRevision("abc");
        chunk.setStartLine(10);chunk.setEndLine(12);chunk.setSymbolName("Order#pay()");chunk.setContent("void pay() {\ncheck();\n}");
        when(chunks.selectById(3L)).thenReturn(chunk);
        when(model.supportsResearch()).thenReturn(true);
    }
    @Test void readsLongMethodTailAndReturnsActualSourceLineRange() {
        chunk.setContent("void pay() {\n" + "// padding\n".repeat(350) + "rollbackPayment();\n}");
        var session=research.begin(1L,"abc",initial());
        assertThat(session.hits().getFirst().excerpt()).doesNotContain("rollbackPayment");
        when(model.research(anyList(),anyList())).thenReturn(Mono.just(turn("read_code","{\"chunkId\":\"3\",\"startLine\":361,\"endLine\":362}")),Mono.just(done()));
        research.explore(session,model,prompt(), ignored -> {}).block();
        assertThat(session.hits()).anySatisfy(hit -> {
            assertThat(hit.startLine()).isEqualTo(361);
            assertThat(hit.excerpt()).contains("rollbackPayment");
        });
        assertThat(session.context().actions().getFirst().status()).isEqualTo("SUCCEEDED");
    }
    @Test void rejectsForeignRevisionAndUnknownToolWithoutReadingForeignCode() {
        var foreign=new KnowledgeChunk();foreign.setId(9L);foreign.setProjectId(9L);foreign.setRevision("secret");
        when(chunks.selectById(9L)).thenReturn(foreign);
        var session=research.begin(1L,"abc",initial());
        when(model.research(anyList(),anyList())).thenReturn(Mono.just(turn("read_code","{\"chunkId\":\"9\"}")),
                Mono.just(turn("shell","{\"command\":\"anything\"}")),Mono.just(done()));
        research.explore(session,model,prompt(),ignored -> {}).block();
        assertThat(session.context().actions()).allSatisfy(action -> assertThat(action.status()).isEqualTo("REJECTED"));
        assertThat(session.hits()).hasSize(1);
        verify(documents,never()).selectById(9L);
    }
    @Test void boundsRepeatedCallsAndPreservesEvidenceInsteadOfFailingTurn() {
        var session=research.begin(1L,"abc",initial());
        when(model.research(anyList(),anyList())).thenReturn(Mono.just(turn("read_code","{\"chunkId\":\"3\"}")));
        research.explore(session,model,prompt(),ignored -> {}).block();
        assertThat(session.context().actions()).hasSize(3);
        assertThat(session.context().warnings()).anyMatch(text -> text.contains("预算"));
        assertThat(session.context().actions().getLast().status()).isEqualTo("REPEATED");
        assertThat(session.hits()).hasSize(1);
    }
    @Test void timesOutResearchAndKeepsAnsweringWithExistingEvidence() {
        properties.setResearchTimeout(Duration.ofMillis(20));
        when(model.research(anyList(),anyList())).thenReturn(Mono.never());
        var session=research.begin(1L,"abc",initial());
        assertThat(research.explore(session,model,prompt(),ignored -> {}).block()).isSameAs(session);
        assertThat(session.context().warnings()).anyMatch(text -> text.contains("补充取证未完成"));
    }
    @Test void rejectsMalformedArgumentsAndHonorsTotalEvidenceBudget() {
        properties.setMaxEvidenceCharacters(2);
        var session=research.begin(1L,"abc",initial());
        assertThat(session.hits()).isEmpty();
        when(model.research(anyList(),anyList())).thenReturn(Mono.just(turn("read_code","{\"chunkId\":\"3\",\"startLine\":9}")),Mono.just(done()));
        research.explore(session,model,prompt(),ignored -> {}).block();
        assertThat(session.context().actions().getFirst().status()).isEqualTo("REJECTED");
        assertThat(session.context().warnings()).anyMatch(text -> text.contains("总预算"));
    }
    @Test void findsRelatedEvidenceOnlyFromExplicitRelationships() {
        var next=new KnowledgeChunk();next.setId(4L);next.setProjectId(1L);next.setRevision("abc");next.setDocumentId(2L);
        next.setStartLine(20);next.setContent("void save() {}"); when(chunks.selectById(4L)).thenReturn(next);
        var related = new RetrievalHitResponse(4L,2L,"Order.java","SOURCE_CODE","METHOD","Order#save()",20,20,1,10,List.of("OUTGOING_METHOD_CALL"),"void save() {}");
        when(retrieval.search(eq(1L),any())).thenReturn(result(List.of(related)));
        var session=research.begin(1L,"abc",initial());
        when(model.research(anyList(),anyList())).thenReturn(Mono.just(turn("related_code","{\"chunkId\":\"3\"}")),Mono.just(done()));
        research.explore(session,model,prompt(),ignored -> {}).block();
        assertThat(session.hits()).extracting(RetrievalHitResponse::chunkId).containsExactly(3L,4L);
    }
    private ProjectConversationPrompt prompt(){ return new ProjectConversationPrompt("test",List.of(new ProjectConversationPrompt.Message("USER","支付失败怎么办"))); }
    private ReviewAgentTurn turn(String name,String args) {
        return new ReviewAgentTurn(new ReviewAgentMessage("assistant","",null,List.of(new ReviewAgentToolCall("call","function",new ReviewAgentToolCall.FunctionCall(name,args)))),0,0,0,"tool_calls");
    }
    private ReviewAgentTurn done(){return new ReviewAgentTurn(new ReviewAgentMessage("assistant","证据准备完成",null,List.of()),0,0,0,"stop");}
    private RetrievalSearchResponse initial(){return result(List.of(new RetrievalHitResponse(3L,2L,"Order.java","SOURCE_CODE","METHOD","Order#pay()",10,12,1,10,List.of(),"preview")));}
    private RetrievalSearchResponse result(List<RetrievalHitResponse> hits){return new RetrievalSearchResponse(1L,"abc","query","v1","LEXICAL","LEXICAL","LOCAL","hash",false,0,false,null,1,false,false,6,5000,10,hits.size(),0,0,hits,List.of());}
}
