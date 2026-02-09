import express from "express";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

/* -------------------------------------------------- */
/* SIMPLE IN-MEMORY CACHE (replace with DB later)    */
/* -------------------------------------------------- */

const roadmapCache = new Map();

/*
Key example:
learn dsa-90
*/
function getCacheKey(goal, days) {
  return `${goal.toLowerCase().trim()}-${days}`;
}

/* -------------------------------------------------- */
/* SAFE JSON PARSER                                  */
/* -------------------------------------------------- */

function tryParseJSON(text) {
  try {
    return JSON.parse(text);
  } catch {
    try {
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

/* -------------------------------------------------- */
/* DEEPSEEK CALL                                     */
/* -------------------------------------------------- */

async function generateFromDeepSeek(prompt) {
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
        temperature: 0.6,
        max_tokens: 4000, // optimized for cost
        response_format: { type: "json_object" }
      })
    }
  );

  const data = await response.json();

  const text = data.choices?.[0]?.message?.content || "";
  if (!text) throw new Error("Empty AI response");

  return text;
}

/* -------------------------------------------------- */
/* MAIN ROUTE                                        */
/* -------------------------------------------------- */

app.post("/generate-roadmap", async (req, res) => {
  try {
    let { goal, days } = req.body;

    if (!goal || !days) {
      return res.status(400).json({ error: "goal and days required" });
    }

    days = Math.min(parseInt(days), 120); // safety cap

    /* ---------------- CACHE CHECK ---------------- */

    const cacheKey = getCacheKey(goal, days);

    if (roadmapCache.has(cacheKey)) {
      console.log("CACHE HIT");
      return res.json(roadmapCache.get(cacheKey));
    }

    console.log("CACHE MISS → generating roadmap");

    /* ---------------- PROMPT ---------------- */

    const prompt = `
    You are an expert goal planner.

    Create a ${days}-day roadmap to achieve this goal:
    "${goal}"

    The goal can be anything:
    - learning a skill
    - fitness (lose/gain weight)
    - building an app
    - productivity habit
    - business
    - lifestyle change

    Rules:
    - Return ONLY valid JSON
    - No explanation text
    - Max 4 tasks per day
    - Tasks must be short (3–6 words)
    - Tasks must be realistic
    - Spread effort across days
    - If days are many → fewer tasks per day
    - If days are few → more tasks per day
    - Adjust difficulty automatically
    - Avoid repetition
    - Make roadmap achievable

    Format:
    {
      "title": "string",
      "durationDays": number,
      "days": [
        { "day": 1, "tasks": ["task1","task2"] }
      ]
    }
    `;


    /* ---------------- AI CALL + RETRY ---------------- */

    let json = null;

    for (let attempt = 0; attempt < 2; attempt++) {
      const text = await generateFromDeepSeek(prompt);
      json = tryParseJSON(text);

      if (json) break;

      console.log("Retrying AI due to JSON issue...");
    }

    if (!json) {
      return res.status(500).json({
        error: "AI returned invalid JSON"
      });
    }

    /* ---------------- CACHE STORE ---------------- */

    roadmapCache.set(cacheKey, json);

    /* ---------------- RESPONSE ---------------- */

    res.json(json);

  } catch (err) {
    console.log("SERVER ERROR:", err.message);
    res.status(500).json({ error: "AI failed" });
  }
});

/* -------------------------------------------------- */

app.get("/ping", (req, res) => res.send("pong"));

app.listen(PORT, () => {
  console.log("Server running on", PORT);
});
