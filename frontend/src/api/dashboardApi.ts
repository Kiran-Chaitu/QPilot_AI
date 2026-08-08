import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';

export interface TestTypeCount {
  type: string;
  count: number;
}

export interface SecurityAdvice {
  category: string;
  severity: string;
  description: string;
  recommendation?: string;
  /** STATIC_ANALYSIS or AI_SUGGESTION — drives the provenance label on the dashboard. */
  origin: string;
  location?: string;
}

/** One stored risk assessment, plotted as a real historical point. */
export interface RiskPoint {
  projectName: string;
  riskScore: number;
  testedSurfacePercent: number;
  recordedAt: string;
}

/** Aggregate over load-test runs the user actually executed. Null when they have run none. */
export interface LoadTestSummary {
  completedRuns: number;
  totalRequests: number;
  avgResponseTimeMs: number;
  avgErrorRatePercent: number;
  lastRunAt?: string;
}

/**
 * Dashboard statistics.
 *
 * <p>The test counts are split deliberately: `totalTestsGenerated` is how many tests exist, while
 * `testsExecuted`/`testsPassed`/`testsFailed` come only from real execution records. A workspace with a
 * large generated suite and no reachable target therefore shows a large generated count and zero passed.
 *
 * <p>`avgTestedSurfacePercent` and `avgRiskScore` are nullable — null means nothing has been analyzed
 * yet, which the UI must render as an empty state rather than as a score of zero.
 */
export interface DashboardStats {
  totalProjects: number;
  analyzedProjects: number;
  totalTestsGenerated: number;
  testsExecuted: number;
  testsPassed: number;
  testsFailed: number;
  testsErrored: number;
  testsNotExecutable: number;
  totalSecurityFindings: number;
  criticalFindings: number;
  highFindings: number;
  mediumFindings: number;
  lowFindings: number;
  avgTestedSurfacePercent?: number | null;
  avgRiskScore?: number | null;
  testDistribution: TestTypeCount[];
  topAdvice: SecurityAdvice[];
  riskHistory: RiskPoint[];
  loadTestSummary?: LoadTestSummary | null;
}

export async function getDashboardStats(): Promise<DashboardStats> {
  const { data } = await httpClient.get<ApiResponse<DashboardStats>>('/dashboard/stats');
  return data.data as DashboardStats;
}
