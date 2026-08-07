import { httpClient } from './httpClient';

export interface AiConfig {
  provider: string;
  hasApiKey: boolean;
  maskedApiKey: string;
  model: string;
  statusMessage: string;
}

export async function getAiConfig(): Promise<AiConfig> {
  const response = await httpClient.get<AiConfig>('/api/ai/config');
  return response.data;
}

export async function updateAiConfig(apiKey: string, model: string): Promise<AiConfig> {
  const response = await httpClient.post<AiConfig>('/api/ai/config', { apiKey, model });
  return response.data;
}
