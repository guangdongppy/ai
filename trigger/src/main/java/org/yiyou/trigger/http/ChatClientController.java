package org.yiyou.trigger.http;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.yiyou.api.IAiService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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


    @GetMapping("/startConversation")
    public String startNewConversation() {
        String conversationId = UUID.randomUUID().toString();
        return conversationId;
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

        SearchRequest request = SearchRequest
                .builder()
                .query(message)
                .topK(5)
                .similarityThreshold(0.5d) // 匹配度
                .filterExpression(expression)// 这里是过滤表达式
                .build();

        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(promptTemplate)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.8d).topK(6).build())
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        String documentsCollectors = documents.stream()
                .map(Document::getText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining());
        // 2. 构建 RAG 消息
        /*Message ragMessage = new SystemPromptTemplate(MyPrompt.SYSTEM_PROMPT.getValue())
                .createMessage(Map.of("documents", documentsCollectors));
        SystemMessage systemMessage = new SystemMessage(ragMessage.getText());*/

        // 3. 加载历史对话
        UserMessage userMessage = new UserMessage(message);
/*        List<Message> history = chatMemory.get(conversationId); // 通过 ID 获取历史
        //history.add(systemMessage);
        history.add(userMessage); // 添加当前用户输入*/

        // 4. 调用模型并流式返回
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .stream()
                .chatResponse();

    }
}