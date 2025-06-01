package org.yiyou.trigger.advisor;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;


@Data
@AllArgsConstructor
public class TimeAdvisor implements BaseAdvisor {

    private final int order;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String time = chatClientRequest.context().get("time").toString();
        String systemMessage = chatClientRequest.prompt().getSystemMessage().getText().replace("{time}", time);

        return chatClientRequest.mutate().prompt(chatClientRequest.prompt().augmentUserMessage(chatClientRequest.prompt().getUserMessage().getText()).augmentSystemMessage(systemMessage)).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer order;

        private Builder() {
        }


        public TimeAdvisor.Builder order(Integer order) {
            this.order = order;
            return this;
        }

        public TimeAdvisor build() {
            return new TimeAdvisor(0);
        }
    }
}
