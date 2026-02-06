import express from "express";
import cors from "cors";

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

// 🔵 TEST ROUTE
app.get("/ping", (req, res) => {
  console.log("PING HIT");
  res.send("pong");
});

// 🟢 MAIN ROADMAP ROUTE
app.post("/generate-roadmap", async (req, res) => {
  try {
    console.log("ROADMAP HIT");
    console.log("BODY:", req.body);

    const { goal, days } = req.body;

    const prompt = `
Create a ${days}-day roadmap for: ${goal}

Return ONLY valid JSON.
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
          "Authorization": `Bearer ${process.env.OPENROUTER_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: "mistralai/mistral-7b-instruct:free",
          messages: [
            { role: "user", content: prompt }
          ]
        })
      }
    );

    const data = await response.json();

    const text = data.choices?.[0]?.message?.content;

    console.log("AI RAW:", text);

    // extract json
    const cleaned = text.replace(/```json/g, "").replace(/```/g, "");
    const json = JSON.parse(cleaned);

    res.json(json);

  } catch (err) {
    console.log("ERROR:", err);
    res.status(500).json({ error: "AI failed" });
  }
});

app.listen(PORT, () => {
  console.log("Server running on port", PORT);
});
