curl -X POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
-H "Authorization: Bearer sk-key" \
-H "Content-Type: application/json" \
-d '{
    "model": "deepseek-r1",
    "messages": [
        {
            "role": "user",
            "content": "9.9和9.11谁大"
        }
    ]
}'