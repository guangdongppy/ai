package org.yiyou.trigger.http;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.yiyou.api.IAiService;
import org.yiyou.trigger.advisor.TimeAdvisor;
import org.yiyou.trigger.tools.DateTimeTools;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ChatClientController implements IAiService {

    @Autowired
    private ChatClient chatClient;
    @Autowired
    JdbcChatMemoryRepository jdbcChatMemoryRepository;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private PromptTemplate promptTemplate;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    ChatClient.Builder chatClientBuilder;
    @Autowired
    private AsyncMcpToolCallbackProvider toolCallbackProvider;
    @Autowired
    private DateTimeTools dateTimeTools;


    @GetMapping("/startConversation")
    public String startNewConversation() {
        return UUID.randomUUID().toString();
    }

    @Override
    @GetMapping("/generate")
    public Map generate(String model, @RequestParam(value = "message", defaultValue = "9.9和9.11谁大") String message) {
        return Map.of("generation", chatClient.prompt().call().content());
    }

    @Override
    @GetMapping("/generateStream")
    public Flux<ChatResponse> generateStream(String model, @RequestParam(value = "message", defaultValue = "Tell me a joke") String message, @RequestParam String conversationId) {

        // 1. 分词与向量检索
        List<Term> termList = HanLP.segment(message);
        List<Object> messageList = termList.stream()
                .filter(term -> StringUtils.isNotBlank(term.word))
                .map(term -> term.word)
                .collect(Collectors.toList());
        FilterExpressionBuilder filterExpression = new FilterExpressionBuilder();
        Filter.Expression expression = filterExpression.in("knowledge", messageList).build();

        Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(
                        // 重写查询
                        RewriteQueryTransformer.builder()
                                .chatClientBuilder(chatClient.mutate())
                                .build(),
                        // 翻译查询
                        TranslationQueryTransformer.builder()
                                .chatClientBuilder(chatClientBuilder)
                                .targetLanguage("chinese")
                                .build()
                )
                // 查询扩展
/*                .queryExpander(MultiQueryExpander.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .numberOfQueries(1)
                        .includeOriginal(false)
                        .build())*/
                // 文档检索
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.4)
                        .topK(20)
                        .vectorStore(vectorStore)
                        // 这里是过滤表达式
                        //.filterExpression(expression)
                        .build())
                // 文档连接
                .documentJoiner(new ConcatenationDocumentJoiner())
                // 去重
                .documentPostProcessors(
                        (query, documents) ->
                                new ArrayList<>(documents.stream()
                                        .map(document -> new Document(document.getText().replaceAll("[\\s\\n\\r]+", " ").trim(), document.getMetadata()))
                                        .collect(
                                                Collectors.toMap(
                                                        Document::getText, // 使用文本作为 key
                                                        doc -> doc, // 使用 Document 作为 value
                                                        (existing, replacement) -> existing // 如果重复，保留第一个
                                                )
                                        )
                                        .values())
                )
                // 查询增强
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        // 允许无上下文
                        .allowEmptyContext(true)
                        .promptTemplate(promptTemplate)
                        .build())
                .build();
        String string = chatMemory.get(conversationId).toString();
        PromptTemplate promptTemplate1 = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("""
                                    "<instructions>
                                    使用LONG_TERM_MEMORY部分的长期对话记忆来提供准确的答案。
                                    ---------------------
                                    LONG_TERM_MEMORY:
                                    <long_term_memory>
                                    ---------------------"    
                        """)
                .build();

        return chatClient.prompt()
                .user(message)
                .advisors(
                        SimpleLoggerAdvisor.builder().build(),
                        TimeAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                        //VectorStoreChatMemoryAdvisor.builder(vectorStore).systemPromptTemplate(promptTemplate1).build(),
                        //retrievalAugmentationAdvisor2
                )
                .advisors(a -> a.params(Map.of(ChatMemory.CONVERSATION_ID, conversationId,
                        "time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒"))
                )))
                //.tools(dateTimeTools)
                .toolCallbacks(toolCallbackProvider.getToolCallbacks())
                .stream()
                .chatResponse();

    }
}