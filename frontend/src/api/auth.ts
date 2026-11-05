import { apiFetch } from './apiFetch';
import type { UserDto } from './types';

export async function fetchCurrentUser(): Promise<UserDto> {
  return apiFetch<UserDto>('/api/auth/me');
}

export async function loginUser(email: string, password: string): Promise<UserDto> {
  return apiFetch<UserDto>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export async function registerUser(
  displayName: string,
  email: string,
  password: string
): Promise<UserDto> {
  return apiFetch<UserDto>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ displayName, email, password }),
  });
}

export async function logoutUser(): Promise<void> {
  await apiFetch<void>('/api/auth/logout', {
    method: 'POST',
  });
}

