export type ProjectSourceType = 'ZIP' | 'GIT_URL' | 'OPENAPI' | 'POSTMAN' | 'WEBSITE_URL' | 'API_URL';
export type ProjectStatus = 'EXTRACTING' | 'UPLOADED' | 'ANALYZING' | 'ANALYZED' | 'FAILED';

export interface ProjectResponse {
  id: number;
  name: string;
  description?: string;
  sourceType: ProjectSourceType;
  repoUrl?: string;
  targetUrl?: string;
  targetApiUrl?: string;
  primaryLanguage?: string;
  /** Files actually indexed. Zero for URL projects, because nothing was downloaded. */
  fileCount?: number;
  status: ProjectStatus;
  hasSwaggerSpec: boolean;
  processingError?: string;
  /** For URL projects: what discovery found and what it could not, so an empty structure is explained. */
  discoveryNotes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApiEndpointSummary {
  httpMethod: string;
  path: string;
  sourceFile: string;
  handlerName: string;
}

export interface KeyFile {
  relativePath: string;
  excerpt: string;
}

export interface ProjectStructureSummary {
  totalFiles: number;
  languageBreakdown: Record<string, number>;
  primaryLanguage?: string;
  dependencies: string[];
  endpoints: ApiEndpointSummary[];
  topLevelEntries: string[];
  keyFiles: KeyFile[];
}

export interface ProjectDetailResponse {
  project: ProjectResponse;
  structure: ProjectStructureSummary | null;
}
