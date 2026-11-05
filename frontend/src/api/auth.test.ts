import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchCurrentUser, loginUser, registerUser, logoutUser } from './auth';

describe('auth api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetchCurrentUser calls /api/auth/me', async () => {
    const mockUser = { id: 'user-1', displayName: 'Jane', email: 'jane@example.com', role: 'ANALYST' };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockUser,
    } as Response);

    const user = await fetchCurrentUser();
    expect(user).toEqual(mockUser);
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({
      credentials: 'same-origin',
    }));
  });

  it('loginUser sends POST to /api/auth/login with credentials', async () => {
    const mockUser = { id: 'user-1', displayName: 'Jane', email: 'jane@example.com', role: 'ANALYST' };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockUser,
    } as Response);

    const user = await loginUser('jane@example.com', 'secretPassword123');
    expect(user).toEqual(mockUser);
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: 'jane@example.com', password: 'secretPassword123' }),
    }));
  });

  it('registerUser sends POST to /api/auth/register', async () => {
    const mockUser = { id: 'user-2', displayName: 'Bob', email: 'bob@example.com', role: 'ANALYST' };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockUser,
    } as Response);

    const user = await registerUser('Bob', 'bob@example.com', 'securePass999');
    expect(user).toEqual(mockUser);
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ displayName: 'Bob', email: 'bob@example.com', password: 'securePass999' }),
    }));
  });

  it('logoutUser sends POST to /api/auth/logout', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => null,
    } as Response);

    await logoutUser();
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({
      method: 'POST',
    }));
  });
});

