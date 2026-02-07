app.post("/generate-roadmap", async (req, res) => {
  try {
    const { goal, days } = req.body;

    const prompt = `
You are a strict JSON generator.

Create a ${days}-day roadmap for: ${goal}

Return ONLY valid JSON.
No explanation.
No markdown.
No backticks.

Format:
{
  "title": "string",
  "durationDays": number,
  "days": [
    { "day": 1, "tasks": ["task1","task2"] }
  ]
}
`;

    const response = await fetch(
      "https://openrouter.ai/api/v1/chat/completions",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${process.env.OPENROUTER_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: "deepseek/deepseek-chat",
          messages: [{ role: "user", content: prompt }],
          max_tokens: 900,
          response_format: { type: "json_object" }
        })
      }
    );

    const data = await response.json();

    if (!response.ok) {
      console.log("OPENROUTER ERROR:", data);
      return res.status(500).json(data);
    }

    const text = data.choices?.[0]?.message?.content;

    if (!text) {
      return res.status(500).json({ error: "Empty AI response", raw: data });
    }

    const json = JSON.parse(text);
    res.json(json);

  } catch (err) {
    console.log(err);
    res.status(500).json({ error: "AI failed" });
  }
});
