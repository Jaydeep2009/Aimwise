import express from "express";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();

/* -------------------------------------------------- */
/* VALIDATE ENVIRONMENT VARIABLES AT STARTUP         */
/* -------------------------------------------------- */

if (!process.env.OPENROUTER_KEY) {
  console.error("FATAL ERROR: OPENROUTER_KEY environment variable is not set");
  console.error("Please set OPENROUTER_KEY in your .env file");
  process.exit(1);
}

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

/* -------------------------------------------------- */
/* CACHE WITH TTL (Time-To-Live)                     */
/* -------------------------------------------------- */

// Cache TTL: 1 hour - balances freshness with API cost reduction
const CACHE_TTL_MS = 60 * 60 * 1000;

const roadmapCache = new Map();

function getCacheKey(goal, days) {
  return `${goal.toLowerCase().trim()}-${days}`;
}

function getCachedRoadmap(key) {
  const entry = roadmapCache.get(key);
  
  if (!entry) {
    return null;
  }
  
  const age = Date.now() - entry.timestamp;
  
  // Cache expired, remove it
  if (age > CACHE_TTL_MS) {
    roadmapCache.delete(key);
    return null;
  }
  
  return entry.data;
}

function setCachedRoadmap(key, data) {
  roadmapCache.set(key, {
    data: data,
    timestamp: Date.now()
  });
}

// Periodic cache cleanup every 10 minutes
setInterval(() => {
  const now = Date.now();
  let evicted = 0;
  
  for (const [key, entry] of roadmapCache.entries()) {
    if (now - entry.timestamp > CACHE_TTL_MS) {
      roadmapCache.delete(key);
      evicted++;
    }
  }
  
  if (evicted > 0) {
    console.log(`Cache cleanup: evicted ${evicted} expired entries`);
  }
}, 10 * 60 * 1000);

/* -------------------------------------------------- */
/* SAFE JSON PARSER WITH VALIDATION                  */
/* -------------------------------------------------- */

// Parses JSON with automatic fixing of common AI response issues:
// - Trailing commas in objects/arrays
// - Extra whitespace
// Returns first 200 chars on error for debugging
function tryParseJSON(text) {
  if (!text || typeof text !== 'string') {
    return { success: false, error: "Empty or invalid text" };
  }
  
  try {
    const parsed = JSON.parse(text);
    return { success: true, data: parsed };
  } catch (firstError) {
    try {
      const fixed = text
        .replace(/,\s*}/g, "}")
        .replace(/,\s*]/g, "]")
        .trim();
      const parsed = JSON.parse(fixed);
      return { success: true, data: parsed };
    } catch (secondError) {
      return { 
        success: false, 
        error: `JSON parsing failed: ${secondError.message}`,
        originalText: text.substring(0, 200)
      };
    }
  }
}

// Validates roadmap structure: title, durationDays, days array with day numbers and tasks
function validateRoadmapStructure(json) {
  if (!json || typeof json !== 'object') {
    return { valid: false, error: "Response is not an object" };
  }
  
  if (!json.title || typeof json.title !== 'string') {
    return { valid: false, error: "Missing or invalid 'title' field" };
  }
  
  if (!json.durationDays || typeof json.durationDays !== 'number') {
    return { valid: false, error: "Missing or invalid 'durationDays' field" };
  }
  
  if (!Array.isArray(json.days)) {
    return { valid: false, error: "Missing or invalid 'days' array" };
  }
  
  for (let i = 0; i < json.days.length; i++) {
    const day = json.days[i];
    
    if (!day || typeof day !== 'object') {
      return { valid: false, error: `Day ${i} is not an object` };
    }
    
    if (typeof day.day !== 'number') {
      return { valid: false, error: `Day ${i} missing 'day' number` };
    }
    
    if (!Array.isArray(day.tasks)) {
      return { valid: false, error: `Day ${i} missing 'tasks' array` };
    }
  }
  
  return { valid: true };
}

/* -------------------------------------------------- */
/* DEEPSEEK CALL WITH BETTER ERROR HANDLING          */
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
        max_tokens: 4000,
        response_format: { type: "json_object" }
      })
    }
  );

  if (!response.ok) {
    throw new Error(`OpenRouter API error: ${response.status} ${response.statusText}`);
  }

  const data = await response.json();

  if (!data.choices || !Array.isArray(data.choices) || data.choices.length === 0) {
    throw new Error("Invalid API response structure: missing choices array");
  }

  const text = data.choices[0]?.message?.content;
  
  if (!text || typeof text !== 'string') {
    throw new Error("Empty or invalid AI response content");
  }

  return text;
}

/* -------------------------------------------------- */
/* INPUT VALIDATION AND SANITIZATION                 */
/* -------------------------------------------------- */

// Validation rules: 3-500 chars, removes injection-prone characters
function validateAndSanitizeGoal(goal) {
  if (!goal || typeof goal !== 'string') {
    return { valid: false, error: "Goal must be a non-empty string" };
  }
  
  const trimmed = goal.trim();
  
  if (trimmed.length === 0) {
    return { valid: false, error: "Goal cannot be empty" };
  }
  
  if (trimmed.length < 3) {
    return { valid: false, error: "Goal must be at least 3 characters long" };
  }
  
  if (trimmed.length > 500) {
    return { valid: false, error: "Goal must be less than 500 characters" };
  }
  
  // Remove potentially dangerous characters for injection prevention
  const sanitized = trimmed.replace(/[<>{}[\]\\]/g, '');
  
  return { valid: true, sanitized: sanitized };
}

// Validation rules: 1-365 days, capped at 120 for cost control
function validateDays(days) {
  const parsed = parseInt(days);
  
  if (isNaN(parsed)) {
    return { valid: false, error: "Days must be a valid number" };
  }
  
  if (parsed < 1) {
    return { valid: false, error: "Days must be at least 1" };
  }
  
  if (parsed > 365) {
    return { valid: false, error: "Days cannot exceed 365" };
  }
  
  // Apply safety cap for cost control
  const capped = Math.min(parsed, 120);
  
  return { valid: true, days: capped };
}

/* -------------------------------------------------- */
/* MAIN ROUTE                                        */
/* -------------------------------------------------- */

// POST /generate-roadmap - Generates AI roadmap, cached for 1 hour
app.post("/generate-roadmap", async (req, res) => {
  try {
    let { goal, days } = req.body;

    // Validate required fields
    if (!goal || !days) {
      return res.status(400).json({ 
        error: "Missing required fields",
        details: "Both 'goal' and 'days' are required" 
      });
    }

    // Validate and sanitize goal
    const goalValidation = validateAndSanitizeGoal(goal);
    if (!goalValidation.valid) {
      return res.status(400).json({ 
        error: "Invalid goal",
        details: goalValidation.error 
      });
    }
    goal = goalValidation.sanitized;

    // Validate days
    const daysValidation = validateDays(days);
    if (!daysValidation.valid) {
      return res.status(400).json({ 
        error: "Invalid days",
        details: daysValidation.error 
      });
    }
    days = daysValidation.days;

    const cacheKey = getCacheKey(goal, days);

    const cachedData = getCachedRoadmap(cacheKey);
    if (cachedData) {
      console.log("CACHE HIT");
      return res.json(cachedData);
    }

    console.log("CACHE MISS → generating roadmap");

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

    let json = null;
    let lastError = null;

    // Retry up to 2 times for AI call, parsing, and validation
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const text = await generateFromDeepSeek(prompt);
        const parseResult = tryParseJSON(text);

        if (!parseResult.success) {
          lastError = parseResult.error;
          console.log(`Attempt ${attempt + 1}: JSON parsing failed - ${parseResult.error}`);
          continue;
        }

        const validation = validateRoadmapStructure(parseResult.data);
        if (!validation.valid) {
          lastError = validation.error;
          console.log(`Attempt ${attempt + 1}: Invalid structure - ${validation.error}`);
          continue;
        }

        json = parseResult.data;
        break;
      } catch (err) {
        lastError = err.message;
        console.log(`Attempt ${attempt + 1}: AI call failed - ${err.message}`);
      }
    }

    if (!json) {
      return res.status(500).json({
        error: "Failed to generate valid roadmap",
        details: lastError || "AI returned invalid response after multiple attempts"
      });
    }

    setCachedRoadmap(cacheKey, json);

    res.json(json);

  } catch (err) {
    console.error("SERVER ERROR:", err.message);
    res.status(500).json({ 
      error: "Internal server error",
      details: process.env.NODE_ENV === 'development' ? err.message : undefined
    });
  }
});

/* -------------------------------------------------- */

// GET /ping - Health check endpoint
app.get("/ping", (_req, res) => res.send("pong"));

/* -------------------------------------------------- */
/* ADJUST ROADMAP ENDPOINT                           */
/* -------------------------------------------------- */

// POST /adjust-roadmap - Redistributes incomplete tasks across remaining days (max 4 per day)
app.post("/adjust-roadmap", async (req, res) => {
  try {
    const { remainingDays, incompleteTasks, totalRemainingDays } = req.body;

    // Validate input
    if (!Array.isArray(remainingDays) || !Array.isArray(incompleteTasks)) {
      return res.status(400).json({
        error: "Invalid input",
        details: "remainingDays and incompleteTasks must be arrays"
      });
    }

    if (typeof totalRemainingDays !== 'number' || totalRemainingDays < 1) {
      return res.status(400).json({
        error: "Invalid totalRemainingDays",
        details: "totalRemainingDays must be a positive number"
      });
    }

    // If no incomplete tasks, return remaining days unchanged
    if (incompleteTasks.length === 0) {
      return res.json({ days: remainingDays });
    }

    // Collect all existing tasks in order
    const allTasks = [];
    remainingDays.forEach(day => {
      if (Array.isArray(day.tasks)) {
        allTasks.push(...day.tasks);
      }
    });

    // Add incomplete tasks at the end (preserving order)
    allTasks.push(...incompleteTasks);

    // Redistribute tasks across remaining days (max 4 per day)
    const adjustedDays = [];
    let taskIndex = 0;
    const maxTasksPerDay = 4;

    for (let i = 0; i < totalRemainingDays; i++) {
      const dayNumber = remainingDays[i]?.day || (i + 1);
      const dayTasks = [];

      // Assign up to maxTasksPerDay tasks
      while (dayTasks.length < maxTasksPerDay && taskIndex < allTasks.length) {
        dayTasks.push(allTasks[taskIndex]);
        taskIndex++;
      }

      // Only add day if it has tasks
      if (dayTasks.length > 0) {
        adjustedDays.push({
          day: dayNumber,
          tasks: dayTasks
        });
      }
    }

    // If there are still tasks left, distribute them across existing days
    if (taskIndex < allTasks.length) {
      let dayIndex = 0;
      while (taskIndex < allTasks.length && dayIndex < adjustedDays.length) {
        if (adjustedDays[dayIndex].tasks.length < maxTasksPerDay) {
          adjustedDays[dayIndex].tasks.push(allTasks[taskIndex]);
          taskIndex++;
        } else {
          dayIndex++;
        }
      }
    }

    res.json({ days: adjustedDays });

  } catch (err) {
    console.error("ADJUST ROADMAP ERROR:", err.message);
    res.status(500).json({
      error: "Internal server error",
      details: process.env.NODE_ENV === 'development' ? err.message : undefined
    });
  }
});

app.listen(PORT, () => {
  console.log("Server running on", PORT);
});
