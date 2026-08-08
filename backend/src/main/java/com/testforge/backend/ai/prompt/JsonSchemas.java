package com.testforge.backend.ai.prompt;

/**
 * Gemini-style structured-output schemas (uppercase Type enum: OBJECT, STRING,
 * ARRAY, INTEGER, NUMBER, BOOLEAN) for each agent, so responses can always be
 * parsed as JSON without fragile prompt-only formatting instructions.
 */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    public static final String CODE_SUMMARY = """
            {
              "type": "OBJECT",
              "properties": {
                "summary": {"type": "STRING", "description": "2-4 sentence plain-English summary of what this project does"},
                "keyResponsibilities": {"type": "ARRAY", "items": {"type": "STRING"}},
                "notableObservations": {"type": "ARRAY", "items": {"type": "STRING"}}
              },
              "required": ["summary", "keyResponsibilities"]
            }
            """;

    public static final String TEST_GENERATION = """
            {
              "type": "OBJECT",
              "properties": {
                "tests": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "type": {"type": "STRING", "enum": ["UNIT", "API", "INTEGRATION", "SECURITY", "EDGE_CASE"]},
                      "title": {"type": "STRING"},
                      "targetName": {"type": "STRING", "description": "class/method or endpoint this test targets"},
                      "framework": {"type": "STRING", "description": "e.g. JUnit5, RestAssured, Postman"},
                      "description": {"type": "STRING"},
                      "code": {"type": "STRING", "description": "runnable test code"}
                    },
                    "required": ["type", "title", "code"]
                  }
                }
              },
              "required": ["tests"]
            }
            """;

    public static final String SECURITY_ANALYSIS = """
            {
              "type": "OBJECT",
              "properties": {
                "findings": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "category": {"type": "STRING", "description": "e.g. SQL_INJECTION, XSS, CSRF, JWT, BROKEN_AUTH, IDOR, SENSITIVE_DATA_EXPOSURE, PRIVILEGE_ESCALATION"},
                      "severity": {"type": "STRING", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]},
                      "description": {"type": "STRING"},
                      "recommendation": {"type": "STRING"},
                      "location": {"type": "STRING"}
                    },
                    "required": ["category", "severity", "description", "recommendation"]
                  }
                }
              },
              "required": ["findings"]
            }
            """;

    /**
     * Recommendations schema. Note it asks for no scores or percentages: the model is given QPilot's
     * measured numbers as input and asked to advise on them, never to produce competing metrics.
     */
    public static final String RECOMMENDATIONS = """
            {
              "type": "OBJECT",
              "properties": {
                "priorityActions": {
                  "type": "ARRAY",
                  "description": "Ordered, concrete next actions, most valuable first",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "title": {"type": "STRING"},
                      "rationale": {"type": "STRING", "description": "why this matters, referencing the supplied measurements"},
                      "effort": {"type": "STRING", "enum": ["LOW", "MEDIUM", "HIGH"]}
                    },
                    "required": ["title", "rationale"]
                  }
                },
                "testStrategy": {"type": "STRING", "description": "2-4 sentences on how to approach testing this specific project"},
                "riskExplanation": {"type": "STRING", "description": "plain-English interpretation of the supplied measured risk inputs"}
              },
              "required": ["priorityActions"]
            }
            """;
}
