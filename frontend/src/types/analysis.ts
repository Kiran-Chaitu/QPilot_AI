export type AnalysisStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';
export type TestType = 'UNIT' | 'API' | 'INTEGRATION' | 'SECURITY' | 'EDGE_CASE';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/**
 * Where a result came from. This drives labelling throughout the UI: a STATIC_ANALYSIS row is a
 * measurement backed by file:line evidence, while an AI_SUGGESTION row is unverified advice. The two
 * must never be rendered identically.
 */
export type ResultOrigin = 'STATIC_ANALYSIS' | 'AI_SUGGESTION';

/**
 * A test's real execution state. Only the `EXECUTED_*` values mean QPilot ran the test and observed an
 * outcome; everything else means the test exists but produced no result.
 */
export type TestExecutionStatus =
  | 'GENERATED'
  | 'NOT_EXECUTABLE'
  | 'SKIPPED'
  | 'EXECUTED_PASSED'
  | 'EXECUTED_FAILED'
  | 'EXECUTION_ERROR';

export interface AnalysisRunResponse {
  id: number;
  status: AnalysisStatus;
  /** Narrative built from measured counts. Always present once the scan stage completes. */
  staticSummary?: string;
  observations: string[];
  /** Present only when an AI provider is configured and responded. */
  aiSummary?: string;
  aiKeyResponsibilities: string[];
  aiNotableObservations: string[];
  /** Explains why AI output is present or absent — shown verbatim so an empty AI panel is never a mystery. */
  aiStatus?: string;
  aiProvider?: string;
  aiEnabled: boolean;
  progressPercent: number;
  currentStage?: string;
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
}

export interface GeneratedTestResponse {
  id: number;
  type: TestType;
  title: string;
  targetName?: string;
  framework?: string;
  description?: string;
  code: string;
  origin: ResultOrigin;
  executionStatus: TestExecutionStatus;
  executionDetail?: string;
  lastExecutedAt?: string;
  executionLatencyMs?: number;
  observedHttpStatus?: number;
  requestMethod?: string;
  requestPath?: string;
  expectedStatusCodes?: string;
  createdAt: string;
}

export interface SecurityFindingResponse {
  id: number;
  category: string;
  severity: Severity;
  description: string;
  recommendation?: string;
  location?: string;
  origin: ResultOrigin;
  /** Populated only for scanned findings — the line the rule matched on. */
  lineNumber?: number;
  /** The actual source line that matched, so the user can verify the finding. */
  evidence?: string;
  ruleId?: string;
  occurrenceCount?: number;
  createdAt: string;
}

/** The counts the risk score was computed from, so inputs can be shown beside the output. */
export interface MeasuredCounts {
  sourceFileCount: number;
  testFileCount: number;
  totalLinesOfCode: number;
  endpointCount: number;
  endpointsReferencedByTests: number;
  criticalFindingCount: number;
  highFindingCount: number;
  mediumFindingCount: number;
  lowFindingCount: number;
}

export interface RiskAssessmentResponse {
  score: number;
  reasons: string[];
  /** The arithmetic behind `score`, one line per contribution. */
  scoreBreakdown: string[];
  testedSurfacePercent: number;
  /** Plain-English statement of what `testedSurfacePercent` actually measured. */
  testedSurfaceBasis?: string;
  coverageGaps: string[];
  /** Checks that could not be performed, with reasons — rendered as "Not available". */
  unavailableChecks: string[];
  measured?: MeasuredCounts;
  createdAt: string;
}

export interface AnalysisResultResponse {
  run: AnalysisRunResponse;
  tests: GeneratedTestResponse[];
  securityFindings: SecurityFindingResponse[];
  risk?: RiskAssessmentResponse;
}

export interface TestExecutionSummary {
  baseUrl?: string;
  totalTests: number;
  executed: number;
  passed: number;
  failed: number;
  errored: number;
  skipped: number;
  notExecutable: number;
  durationMs: number;
  executedAt: string;
  results: GeneratedTestResponse[];
}
