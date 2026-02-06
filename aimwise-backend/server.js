import express from "express";
import fetch from "node-fetch";

const app = express();
app.use(express.json());

app.post("/generate-roadmap", async (req, res) => {
  try {
    const { goal, days } = req.body;

    const prompt = `
Create a ${days}-day roadmap for: ${goal}

Return ONLY JSON in this format:
{
 "title": "...",
 "durationDays": ${days},
 "days":[
   {
     "day":1,
     "tasks":["task1","task2"]
   }
 ]
}
`;

    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${process.env.OPENROUTER_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: "arcee-ai/trinity-large-preview",
        messages: [
          { role: "user", content: prompt }
        ]
      })
    });

    const data = await response.json();

    const text = data.choices[0].message.content;

    const jsonStart = text.indexOf("{");
    const jsonEnd = text.lastIndexOf("}");
    const clean = text.substring(jsonStart, jsonEnd + 1);

    const parsed = JSON.parse(clean);

    res.json(parsed);

  } catch (err) {
    console.log("AI ERROR:", err);
    res.status(500).json({ error: "AI failed" });
  }
});

app.listen(3000, () => console.log("Server running"));
