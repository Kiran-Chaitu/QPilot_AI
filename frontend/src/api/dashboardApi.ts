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
  recommendation: string;
}

export interface DashboardStats {
  totalProjects: number;
  analyzedProjects: number;
  totalTestsGenerated: number;
  totalSecurityFindings: number;
  avgCoveragePercent: number;
  avgRiskScore: number;
  testDistribution: TestTypeCount[];
  topAdvice: SecurityAdvice[];
}

export async function getDashboardStats(): Promise<DashboardStats> {
  const { data } = await httpClient.get<ApiResponse<DashboardStats>>('/dashboard/stats');
  return data.data as DashboardStats;
}
