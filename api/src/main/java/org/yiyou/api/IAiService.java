package org.yiyou.api;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

public interface IAiService {

     Map generate(String model, String message);


    Flux<ChatResponse> generateStream(String model, String message, String conversationId);
}
