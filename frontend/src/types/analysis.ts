export type AnalysisStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';
export type TestType = 'UNIT' | 'API' | 'INTEGRATION' | 'SECURITY' | 'EDGE_CASE';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface AnalysisRunResponse {
  id: number;
  status: AnalysisStatus;
  codeSummary?: string;
  keyResponsibilities: string[];
  notableObservations: string[];
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
  aiProvider: string;
}

export interface GeneratedTestResponse {
  id: number;
  type: TestType;
  title: string;
  targetName: string;
  framework: string;
  description: string;
  code: string;
  createdAt: string;
}

export interface SecurityFindingResponse {
  id: number;
  category: string;
  severity: Severity;
  description: string;
  recommendation: string;
  location: string;
  createdAt: string;
}

export interface RiskAssessmentResponse {
  score: number;
  reasons: string[];
  coverageEstimatePercent: number;
  coverageGaps: string[];
  createdAt: string;
}

export interface AnalysisResultResponse {
  run: AnalysisRunResponse;
  tests: GeneratedTestResponse[];
  securityFindings: SecurityFindingResponse[];
  risk?: RiskAssessmentResponse;
}
