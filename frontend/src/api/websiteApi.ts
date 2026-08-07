import { httpClient } from './httpClient';

export interface HeaderAuditItem {
  name: string;
  value: string;
  present: boolean;
  status: string;
}

export interface LinkAuditItem {
  url: string;
  statusCode: number;
  statusText: string;
  responseTimeMs: number;
}

export interface WebsiteAuditResponse {
  targetUrl: string;
  responseCode: number;
  responseTimeMs: number;
  pageTitle: string;
  performanceScore: number;
  accessibilityScore: number;
  bestPracticesScore: number;
  seoScore: number;
  securityScore: number;
  headers: HeaderAuditItem[];
  links: LinkAuditItem[];
  recommendations: string[];
}

export async function runWebsiteAudit(targetUrl: string): Promise<WebsiteAuditResponse> {
  const response = await httpClient.post<WebsiteAuditResponse>('/website/audit', { targetUrl });
  return response.data;
}
