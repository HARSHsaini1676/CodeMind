package com.arshh.codemind.answering;

import com.arshh.codemind.dto.AskResponse;
import com.arshh.codemind.dto.Citation;
import com.arshh.codemind.dto.SearchResult;
import com.arshh.codemind.retrieval.ContextAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * Synthesizes grounded answers to user questions using retrieved code chunks and LLM.
 * Supports both standard synchronous answers and real-time streaming tokens.
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private static final String SYSTEM_PROMPT = """
            You are CodeMind, an AI software engineering assistant helping a developer understand a codebase.
            Answer the user's question using ONLY the provided code snippets.
            
            Rules:
            1. Reference evidence by placing its chunk ID in brackets like [CHUNK_ID: <id>] when explaining code.
            2. Do not fabricate, hallucinate, or guess file paths or line numbers.
            3. If the provided snippets do not have enough evidence to answer the question, state clearly that repository evidence is insufficient.
            4. Keep the explanation concise, clear, and direct.
            """;

    private final ChatModel chatModel;
    private final ContextAssembler contextAssembler;
    private final CitationValidator citationValidator;

    public AnswerService(
            ChatModel chatModel,
            ContextAssembler contextAssembler,
            CitationValidator citationValidator) {
        this.chatModel = chatModel;
        this.contextAssembler = contextAssembler;
        this.citationValidator = citationValidator;
    }

    /**
     * Generates a grounded response with validated citations based on retrieved code chunks.
     */
    public AskResponse answerQuestion(String question, List<SearchResult> retrievedChunks) {
        // Return clear notice if no relevant chunks were retrieved
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return new AskResponse(
                    "I could not find any relevant code snippets in the repository to answer your question.",
                    Collections.emptyList(),
                    List.of("No matching code chunks found above search threshold.")
            );
        }

        // Assemble retrieved chunks into bounded prompt context
        String contextText = contextAssembler.assembleContext(retrievedChunks);
        String userPrompt = "CODE EVIDENCE:\n" + contextText + "\n\nQUESTION: " + question;

        log.info("Sending prompt to ChatModel for question: {}", question);
        String rawAnswer = chatModel.call(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt));

        // Validate citations against retrieved candidate chunks
        List<Citation> verifiedCitations = citationValidator.validateCitations(rawAnswer, retrievedChunks);

        List<String> limitations = List.of(
                "Static analysis and semantic search may not reflect dynamic runtime behavior or runtime configuration."
        );

        return new AskResponse(rawAnswer, verifiedCitations, limitations);
    }

    /**
     * Streams the generated answer token-by-token using reactive Flux for real-time SSE streaming.
     */
    public Flux<String> streamAnswer(String question, List<SearchResult> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return Flux.just("I could not find any relevant code snippets in the repository to answer your question.");
        }

        String contextText = contextAssembler.assembleContext(retrievedChunks);
        String userPrompt = "CODE EVIDENCE:\n" + contextText + "\n\nQUESTION: " + question;

        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt)));

        log.info("Streaming response from ChatModel for question: {}", question);
        return chatModel.stream(prompt)
                .map(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        String text = response.getResult().getOutput().getText();
                        return text != null ? text : "";
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty());
    }
}
