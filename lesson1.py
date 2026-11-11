import anthropic

client = anthropic.Anthropic()

response = client.messages.create(
    model="claude-haiku-5-5",
    max_tokens=1024,
    messages=[
        {"role": "user", "content": "Say hello in Tamil and Hindi"}
    ],
)

for block in response.content:
    if block.type == "text":
        print(block.text)
