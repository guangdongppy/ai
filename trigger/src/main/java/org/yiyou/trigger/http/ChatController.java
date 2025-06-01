package org.yiyou.trigger.http;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.yiyou.api.IAiService;
import org.yiyou.trigger.enums.MyPrompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/ai")
public class ChatController implements IAiService {

    @Autowired
    private ChatModel chatModel;
    @Autowired
    private VectorStore vectorStore;

    @Override
    @GetMapping("/generate")
    public Map generate(String model, @RequestParam(value = "message", defaultValue = "9.9和9.11谁大") String message) {
        String call = this.chatModel.call(message);
        System.out.println(call);
        return Map.of("generation", call);
    }

    @Override
    @GetMapping("/generateStream")
    public Flux<ChatResponse> generateStream(String model, @RequestParam(value = "message", defaultValue = "Tell me a joke") String message, @RequestParam String conversationId) {

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

        List<Document> documents = vectorStore.similaritySearch(request);
        String documentsCollectors = documents.stream().map(Document::getText).map(String::trim).distinct().collect(Collectors.joining());
        System.out.println(documentsCollectors);

        Message ragMessage = new SystemPromptTemplate(MyPrompt.SYSTEM_PROMPT.getValue()).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        //messages.add(new UserMessage(message));
        messages.add(ragMessage);
        messages.add(new SystemMessage(MyPrompt.SYSTEM_PROMPT.getValue()));

        return this.chatModel.stream(new Prompt(messages));
    }
}