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

    public static final String RISK_SCORE = """
            {
              "type": "OBJECT",
              "properties": {
                "score": {"type": "INTEGER", "description": "overall risk score from 0 (safe) to 100 (critical)"},
                "reasons": {"type": "ARRAY", "items": {"type": "STRING"}},
                "coverageEstimatePercent": {"type": "INTEGER", "description": "estimated percent of the codebase likely covered by tests"},
                "coverageGaps": {"type": "ARRAY", "items": {"type": "STRING"}}
              },
              "required": ["score", "reasons", "coverageEstimatePercent", "coverageGaps"]
            }
            """;
}
