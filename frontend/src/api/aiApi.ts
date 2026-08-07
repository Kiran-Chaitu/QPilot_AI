import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';

export interface AiConfig {
  provider: string;
  hasApiKey: boolean;
  maskedApiKey: string;
  model: string;
  statusMessage: string;
}

export async function getAiConfig(): Promise<AiConfig> {
  const { data } = await httpClient.get<ApiResponse<AiConfig>>('/ai/config');
  return data.data as AiConfig;
}

export async function updateAiConfig(apiKey: string, model: string): Promise<AiConfig> {
  const { data } = await httpClient.post<ApiResponse<AiConfig>>('/ai/config', { apiKey, model });
  return data.data as AiConfig;
}
