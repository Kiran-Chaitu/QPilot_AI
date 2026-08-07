import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';

export async function login(request: LoginRequest): Promise<AuthResponse> {
  const { data } = await httpClient.post<ApiResponse<AuthResponse>>('/auth/login', request);
  return data.data as AuthResponse;
}

export async function register(request: RegisterRequest): Promise<AuthResponse> {
  const { data } = await httpClient.post<ApiResponse<AuthResponse>>('/auth/register', request);
  return data.data as AuthResponse;
}
