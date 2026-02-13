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

const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

const roadmapCache = new Map();

/*
Cache entry structure:
{
  data: <roadmap object>,
  timestamp: <creation time in ms>
}
*/

/**
 * Generates a cache key from goal and days parameters.
 * 
 * @param {string} goal - The goal string
 * @param {number} days - Number of days
 * @returns {string} Cache key in format "goal-days" (lowercase, trimmed)
 * 
 * @example
 * getCacheKey("Learn React", 30) // returns "learn react-30"
 */
function getCacheKey(goal, days) {
  return `${goal.toLowerCase().trim()}-${days}`;
}

/**
 * Retrieves a cached roadmap if it exists and hasn't expired.
 * 
 * @param {string} key - Cache key
 * @returns {Object|null} Cached roadmap data or null if not found/expired
 */
function getCachedRoadmap(key) {
  const entry = roadmapCache.get(key);
  
  if (!entry) {
    return null;
  }
  
  const age = Date.now() - entry.timestamp;
  
  if (age > CACHE_TTL_MS) {
    // Cache expired, remove it
    roadmapCache.delete(key);
    return null;
  }
  
  return entry.data;
}

/**
 * Stores a roadmap in the cache with current timestamp.
 * 
 * @param {string} key - Cache key
 * @param {Object} data - Roadmap data to cache
 */
function setCachedRoadmap(key, data) {
  roadmapCache.set(key, {
    data: data,
    timestamp: Date.now()
  });
}

// Periodic cache cleanup (every 10 minutes)
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

/**
 * Attempts to parse JSON text with automatic fixing of common issues.
 * 
 * @param {string} text - JSON text to parse
 * @returns {Object} Result object with success flag and data/error
 * @returns {boolean} returns.success - Whether parsing succeeded
 * @returns {Object} [returns.data] - Parsed JSON object (if success)
 * @returns {string} [returns.error] - Error message (if failed)
 * @returns {string} [returns.originalText] - First 200 chars of input (if failed)
 * 
 * @example
 * tryParseJSON('{"key": "value"}') // { success: true, data: { key: "value" } }
 * tryParseJSON('invalid') // { success: false, error: "JSON parsing failed: ..." }
 */
function tryParseJSON(text) {
  if (!text || typeof text !== 'string') {
    return { success: false, error: "Empty or invalid text" };
  }
  
  try {
    const parsed = JSON.parse(text);
    return { success: true, data: parsed };
  } catch (firstError) {
    try {
      // Try fixing common JSON issues
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
        originalText: text.substring(0, 200) // First 200 chars for debugging
      };
    }
  }
}

/**
 * Validates that a parsed JSON object has the correct roadmap structure.
 * 
 * @param {Object} json - Parsed JSON object to validate
 * @returns {Object} Validation result
 * @returns {boolean} returns.valid - Whether structure is valid
 * @returns {string} [returns.error] - Error message if invalid
 * 
 * @example
 * validateRoadmapStructure({ title: "Goal", durationDays: 30, days: [] })
 * // { valid: true }
 */
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

/**
 * Calls the DeepSeek AI model via OpenRouter API to generate content.
 * 
 * @param {string} prompt - The prompt to send to the AI
 * @returns {Promise<string>} AI-generated text response
 * @throws {Error} If API call fails or response is invalid
 * 
 * @example
 * const response = await generateFromDeepSeek("Create a learning plan...");
 */
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

/**
 * Validates and sanitizes a goal string.
 * 
 * @param {string} goal - Goal string to validate
 * @returns {Object} Validation result
 * @returns {boolean} returns.valid - Whether goal is valid
 * @returns {string} [returns.sanitized] - Sanitized goal string (if valid)
 * @returns {string} [returns.error] - Error message (if invalid)
 * 
 * @example
 * validateAndSanitizeGoal("Learn React")
 * // { valid: true, sanitized: "Learn React" }
 * 
 * @example
 * validateAndSanitizeGoal("ab")
 * // { valid: false, error: "Goal must be at least 3 characters long" }
 */
function validateAndSanitizeGoal(goal) {
  if (!goal || typeof goal !== 'string') {
    return { valid: false, error: "Goal must be a non-empty string" };
  }
  
  // Trim whitespace
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
  // Allow letters, numbers, spaces, and common punctuation
  const sanitized = trimmed.replace(/[<>{}[\]\\]/g, '');
  
  return { valid: true, sanitized: sanitized };
}

/**
 * Validates and normalizes the days parameter.
 * 
 * @param {number|string} days - Number of days (will be parsed if string)
 * @returns {Object} Validation result
 * @returns {boolean} returns.valid - Whether days value is valid
 * @returns {number} [returns.days] - Validated and capped days value (if valid)
 * @returns {string} [returns.error] - Error message (if invalid)
 * 
 * @example
 * validateDays(30) // { valid: true, days: 30 }
 * validateDays(500) // { valid: true, days: 120 } // capped at 120
 * validateDays(-5) // { valid: false, error: "Days must be at least 1" }
 */
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
  
  // Apply safety cap
  const capped = Math.min(parsed, 120);
  
  return { valid: true, days: capped };
}

/* -------------------------------------------------- */
/* MAIN ROUTE                                        */
/* -------------------------------------------------- */

/**
 * POST /generate-roadmap
 * 
 * Generates a personalized learning/achievement roadmap using AI.
 * Results are cached for 1 hour to improve performance and reduce API costs.
 * 
 * @route POST /generate-roadmap
 * @access Public
 * 
 * @param {Object} req.body - Request body
 * @param {string} req.body.goal - The goal to create a roadmap for (3-500 characters)
 * @param {number} req.body.days - Number of days for the roadmap (1-365, capped at 120)
 * 
 * @returns {Object} 200 - Success response with roadmap data
 * @returns {string} returns.title - Title of the roadmap
 * @returns {number} returns.durationDays - Total duration in days
 * @returns {Array<Object>} returns.days - Array of daily plans
 * @returns {number} returns.days[].day - Day number (1-indexed)
 * @returns {Array<string>} returns.days[].tasks - Array of tasks for the day (max 4 tasks)
 * 
 * @returns {Object} 400 - Bad request (missing or invalid parameters)
 * @returns {string} returns.error - Error type
 * @returns {string} returns.details - Detailed error message
 * 
 * @returns {Object} 500 - Internal server error
 * @returns {string} returns.error - Error type
 * @returns {string} returns.details - Error details (only in development mode)
 * 
 * @example
 * // Request
 * POST /generate-roadmap
 * Content-Type: application/json
 * {
 *   "goal": "Learn React Native",
 *   "days": 30
 * }
 * 
 * @example
 * // Success Response (200)
 * {
 *   "title": "Learn React Native",
 *   "durationDays": 30,
 *   "days": [
 *     {
 *       "day": 1,
 *       "tasks": ["Setup development environment", "Learn JavaScript basics"]
 *     },
 *     {
 *       "day": 2,
 *       "tasks": ["Study React fundamentals", "Build first component"]
 *     }
 *   ]
 * }
 * 
 * @example
 * // Error Response (400)
 * {
 *   "error": "Invalid goal",
 *   "details": "Goal must be at least 3 characters long"
 * }
 */
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

    /* ---------------- CACHE CHECK ---------------- */

    const cacheKey = getCacheKey(goal, days);

    const cachedData = getCachedRoadmap(cacheKey);
    if (cachedData) {
      console.log("CACHE HIT");
      return res.json(cachedData);
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
    let lastError = null;

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

    /* ---------------- CACHE STORE ---------------- */

    setCachedRoadmap(cacheKey, json);

    /* ---------------- RESPONSE ---------------- */

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

/**
 * GET /ping
 * 
 * Health check endpoint to verify server is running.
 * 
 * @route GET /ping
 * @access Public
 * 
 * @returns {string} 200 - Returns "pong" if server is healthy
 * 
 * @example
 * // Request
 * GET /ping
 * 
 * @example
 * // Response (200)
 * pong
 */
app.get("/ping", (req, res) => res.send("pong"));

app.listen(PORT, () => {
  console.log("Server running on", PORT);
});
