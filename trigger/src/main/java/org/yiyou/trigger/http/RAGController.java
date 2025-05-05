package org.yiyou.trigger.http;


import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.yiyou.api.IRAGService;
import org.yiyou.api.response.Response;
import org.yiyou.trigger.constant.TriggerRedisConstant;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/ai/rag/")
public class RAGController implements IRAGService {
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    @GetMapping("query_rag_tag_list")
    public Response<List<String>> queryRagTagList() {
        List<String> elements = stringRedisTemplate.opsForList().range(TriggerRedisConstant.TAG_RAG_KEY, 0, -1);
        return Response.<List<String>>builder()
                .code("0000")
                .message("调用成功")
                .data(elements)
                .build();
    }

    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam("ragTag") String ragTag, @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库开始 {}", ragTag);
        for (MultipartFile file : files) {
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentReader.get();
            List<Document> documentSplitterList = new ArrayList<>();

            for (Document doc : documents) {
                String text = doc.getText();
                List<Term> termList = HanLP.segment(text);
                termList.forEach(term -> {
                    if(StringUtils.isNotBlank(term.word)){
                        Document segmentedDoc = new Document(text);
                        segmentedDoc.getMetadata().put("knowledge", term.word);
                        documentSplitterList.add(segmentedDoc);
                    }
                });
            }

            vectorStore.accept(documentSplitterList);

            List<String> elements = stringRedisTemplate.opsForList().range(TriggerRedisConstant.TAG_RAG_KEY, 0, -1);
            if (elements == null || !elements.contains(ragTag)) {
                Assert.isTrue(stringRedisTemplate.opsForList().rightPush(TriggerRedisConstant.TAG_RAG_KEY, ragTag) > elements.size(), "添加标签失败");
            }
        }

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("0000").message("调用成功").build();
    }

}
