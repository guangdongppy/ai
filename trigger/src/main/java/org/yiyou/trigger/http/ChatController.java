package org.yiyou.trigger.http;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.yiyou.api.IAiService;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequestMapping("/ai")
public class ChatController implements IAiService {

    @Autowired
    private  OpenAiChatModel chatModel;
    @Autowired
    private  PgVectorStore pgVectorStore;

    @Autowired
    public ChatController(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    @GetMapping("/generate")
    public Map generate(String model, @RequestParam(value = "message", defaultValue = "9.9和9.11谁大") String message) {
        String call = this.chatModel.call(message);
        System.out.println(call);
        return Map.of("generation", call);
    }

    @Override
    @GetMapping("/generateStream")
    public Flux<ChatResponse> generateStream(String model, @RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        Prompt prompt = new Prompt(new UserMessage(message));
        return this.chatModel.stream(prompt);
    }
}