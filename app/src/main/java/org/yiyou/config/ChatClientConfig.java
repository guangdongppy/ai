package org.yiyou.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    /**
     * 创建ChatClient
     * 方式一
     * @param chatModel
     * @return
     */
/*    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }*/

    /**
     * 创建ChatClient
     * 方式二
     * @return
     */
/*    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        return chatClientBuilder.defaultSystem("")
                .defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(vectorStore).build())
                .build();
    }*/

    /**
     * 创建ChatClient
     * 方式三
     * @return
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {

        return chatClientBuilder
                .defaultSystem("现在是北京时间{time}，你是一个人工智能，你的目的是帮助用户解答问题和提供信息，请你优先通过会话历史回答问题。")// ,你是一名虚构的动漫里小熊的智能客服，你的名字叫一二。请以友好、热情、可爱的方式回答用户问题。
                //.defaultAdvisors(SimpleLoggerAdvisor.builder().build(), MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository){
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(200)
                .build();
    }

    @Bean
    public PromptTemplate promptTemplate() {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
/*                .template("""
            上下文信息如下。

			---------------------
			<question_answer_context>
			---------------------

			根据上下文信息且没有先验知识，回答查询。

			遵循以下规则：

			1. 如果答案不在上下文中，只需说你不知道。
			2. 避免使用"根据上下文..."或"提供的信息..."等语句。
            """)*/
                .template("""
                        上下文信息在下面。         
                        ---------------------
                        <context>
                        ---------------------       
                        给定上下文信息和无先前知识，回答查询。       
                        遵守这些规则：   
                        1. 如果答案不在上下文中，就直接说你不知道。
                        2. 避免使用“根据上下文…”或“根据你提供的信息…”这样的表述。   
                        查询：<query>
                        答案：           
            """)
                .build();
    }

}
