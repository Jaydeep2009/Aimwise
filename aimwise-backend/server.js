import express from "express";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

/* ---------------- TEST ---------------- */
app.get("/ping", (req, res) => {
  res.send("pong");
});

/* ---------------- JSON FIXER ---------------- */
function tryParseJSON(text) {
  try {
    return JSON.parse(text);
  } catch {
    try {
      // remove trailing commas
      const fixed = text
        .replace(/,\s*}/g, "}")
        .replace(/,\s*]/g, "]")
        .trim();

      return JSON.parse(fixed);
    } catch {
      return null;
    }
  }
}

/* ---------------- GEMINI CALL ---------------- */
async function generateFromGemini(prompt) {
  const model = "gemini-2.5-flash";

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${process.env.GEMINI_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 4000,
          responseMimeType: "application/json",

          // 🔥 VERY IMPORTANT → stops JSON truncation
          thinkingConfig: { thinkingBudget: 0 }
        }
      })
    }
  );

  const data = await response.json();
  console.log("GEMINI RAW RECEIVED");

  const text =
    data.candidates?.[0]?.content?.parts?.[0]?.text || "";

  if (!text) throw new Error("Empty AI response");

  return text;
}

/* ---------------- MAIN ROUTE ---------------- */
app.post("/generate-roadmap", async (req, res) => {
  try {
    const { goal, days } = req.body;

    const prompt = `
You are a strict JSON generator.

Create a ${days}-day roadmap for: ${goal}

Rules:
- Max 3 tasks per day
- Keep tasks short
- Return ONLY valid JSON
- No explanation
- No markdown

Format:
{
  "title": "string",
  "durationDays": number,
  "days": [
    { "day": 1, "tasks": ["task1","task2"] }
  ]
}
`;

    /* ---- try up to 2 times ---- */
    for (let attempt = 0; attempt < 2; attempt++) {
      const text = await generateFromGemini(prompt);

      const json = tryParseJSON(text);
      if (json) return res.json(json);

      console.log("Retrying Gemini due to broken JSON...");
    }

    res.status(500).json({
      error: "AI returned invalid JSON after retry"
    });

  } catch (err) {
    console.log(err);
    res.status(500).json({ error: "AI failed" });
  }
});

app.listen(PORT, () => {
  console.log("Server running on", PORT);
});
