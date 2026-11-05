package com.arshh.codemind.evaluation;

import com.arshh.codemind.answering.AnswerService;
import com.arshh.codemind.answering.CitationValidator;
import com.arshh.codemind.dto.AskResponse;
import com.arshh.codemind.dto.Citation;
import com.arshh.codemind.dto.SearchResult;
import com.arshh.codemind.retrieval.ContextAssembler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Benchmark evaluation tests for Answering, Citation Verification, and Hallucination Resistance.
 * Verifies the 4 core dimensions documented in evaluation/evaluation-notes.md:
 *  1. Server-verified citation validity (target: 100%)
 *  2. Hallucination stripping of non-existent chunk IDs
 *  3. Negative query refusal / insufficient evidence handling (target: 100%)
 *  4. Bounded prompt context assembly with parent scope tagging
 */
class BenchmarkAnsweringEvaluationTest {

    private final CitationValidator citationValidator = new CitationValidator();
    private final ContextAssembler contextAssembler = new ContextAssembler();

    @Test
    @DisplayName("Citation Validity: 100% resolution of real retrieved chunk citations")
    void testCitationValidityTarget100Percent() {
        // Given retrieved code chunks from Qdrant
        SearchResult chunk1 = new SearchResult(
                "chunk-jwt-001",
                "src/main/java/com/arshh/codemind/security/JwtFilter.java",
                15, 45, "doFilterInternal", 0.95f,
                "public void doFilterInternal(HttpServletRequest request) { ... }"
        );
        SearchResult chunk2 = new SearchResult(
                "chunk-val-002",
                "src/main/java/com/arshh/codemind/ingestion/GitHubUrlValidator.java",
                10, 30, "validateAndNormalize", 0.91f,
                "public RepositoryCoordinates validateAndNormalize(String rawUrl) { ... }"
        );
        List<SearchResult> retrieved = List.of(chunk1, chunk2);

        // When LLM generates an answer referencing the chunk ID
        String llmAnswer = "The JWT authentication logic is implemented in [CHUNK_ID: chunk-jwt-001] within doFilterInternal.";

        List<Citation> citations = citationValidator.validateCitations(llmAnswer, retrieved);

        // Then citations must resolve exactly to real repository coordinates with 0 hallucinated line numbers
        assertThat(citations).hasSize(1);
        Citation verified = citations.get(0);
        assertThat(verified.chunkId()).isEqualTo("chunk-jwt-001");
        assertThat(verified.path()).isEqualTo("src/main/java/com/arshh/codemind/security/JwtFilter.java");
        assertThat(verified.startLine()).isEqualTo(15);
        assertThat(verified.endLine()).isEqualTo(45);
    }

    @Test
    @DisplayName("Hallucination Resistance: Strips fabricated or non-existent chunk IDs")
    void testHallucinationResistanceDiscardsFakeChunks() {
        SearchResult realChunk = new SearchResult(
                "chunk-real-001",
                "src/main/java/com/arshh/codemind/retrieval/RetrievalService.java",
                20, 60, "retrieve", 0.88f,
                "public List<SearchResult> retrieve(...) { ... }"
        );
        List<SearchResult> retrieved = List.of(realChunk);

        // LLM invents a chunk ID that was never in the retrieval pool
        String hallucinatoryAnswer = "Configured in [CHUNK_ID: chunk-fake-999] and also [CHUNK_ID: chunk-real-001].";

        List<Citation> citations = citationValidator.validateCitations(hallucinatoryAnswer, retrieved);

        // Only the genuine chunk must be accepted; the fake chunk is discarded
        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).chunkId()).isEqualTo("chunk-real-001");
    }

    @Test
    @DisplayName("Negative Queries: Returns insufficient evidence without fabricating citations for absent features")
    void testNegativeQueryRefusalWithoutEvidence() {
        ChatModel mockChatModel = mock(ChatModel.class);
        AnswerService answerService = new AnswerService(mockChatModel, contextAssembler, citationValidator);

        // Negative query scenario (e.g. q12 Stripe webhooks or q13 Redis cache where 0 chunks match)
        AskResponse response = answerService.answerQuestion(
                "Where is the Stripe payment gateway webhook handler configured?",
                Collections.emptyList()
        );

        assertThat(response.answer()).containsIgnoringCase("could not find any relevant code snippets");
        assertThat(response.citations()).isEmpty();
        assertThat(response.limitations()).isNotEmpty();
    }

    @Test
    @DisplayName("Context Assembler: Correctly attaches parent scope headers and stays within token budget")
    void testContextAssemblerParentScopeAndBudget() {
        SearchResult chunk = new SearchResult(
                "chunk-repo-001",
                "src/main/java/com/arshh/codemind/repository/RepositoryStateStore.java",
                10, 35, "RepositoryStateStore", 0.89f,
                "@Service\npublic class RepositoryStateStore { ... }"
        );

        String context = contextAssembler.assembleContext(List.of(chunk));

        assertThat(context).contains("[CHUNK_ID: chunk-repo-001]");
        assertThat(context).contains("FILE: src/main/java/com/arshh/codemind/repository/RepositoryStateStore.java (Lines 10-35)");
        assertThat(context).contains("SCOPE / CONTEXT: Symbol: RepositoryStateStore (Enclosed in RepositoryStateStore.java)");
        assertThat(context.length()).isLessThan(12000);
    }
}
