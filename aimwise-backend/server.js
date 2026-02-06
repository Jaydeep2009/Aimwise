import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import { GoogleGenerativeAI } from "@google/generative-ai";

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

const genAI = new GoogleGenerativeAI(process.env.GEMINI_KEY);

// -------------------------------
// Generate Roadmap API
// -------------------------------
app.post("/generate-roadmap", async (req, res) => {
  try {
    const { goal,days } = req.body;
    const totalDays = Math.min(Math.max(days || 30, 1), 90);


    if (!goal) {
      return res.status(400).json({ error: "Goal is required" });
    }

    const model = genAI.getGenerativeModel({
      model: "gemini-2.5-flash"
    });

    const prompt = `
You are a roadmap generator.

Goal: ${goal}
Create a ${totalDays}-day roadmap.
Return ONLY valid JSON.
Do NOT use markdown.
Do NOT wrap in \`\`\`.
Do NOT add explanations.

Format exactly like this:

{
  "title": "",
  "durationDays": ${totalDays},
  "days": [
    {
      "day": 1,
      "tasks": []
    }
  ]
}

Rules:
- ${totalDays} days
- 2-4 tasks per day
- practical tasks
`;

    const result = await model.generateContent(prompt);
    let text = result.response.text();

    // -------------------------------
    // CLEAN RESPONSE (important)
    // -------------------------------
    text = text
      .replace(/```json/g, "")
      .replace(/```/g, "")
      .trim();

    let json;

    try {
      json = JSON.parse(text);
    } catch (e) {
      console.log("Raw AI response:", text);
      return res.status(500).json({
        error: "AI returned invalid JSON",
        raw: text
      });
    }

    res.json(json);

  } catch (error) {
    console.error(error);
    res.status(500).json({
      error: "Server error",
      message: error.message
    });
  }
});

//ping
app.get("/ping", (req,res)=>{
  console.log("PING HIT");
  res.send("ok");
});


// -------------------------------
app.listen(3000, () => {
  console.log("Server running on port 3000");
});
