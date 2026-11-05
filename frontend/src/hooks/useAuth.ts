import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchCurrentUser, loginUser, registerUser, logoutUser } from '../api/auth';
import type { UserDto } from '../api/types';

export function useAuth() {
  const queryClient = useQueryClient();

  const {
    data: user,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery<UserDto | null, Error>({
    queryKey: ['auth', 'me'],
    queryFn: async () => {
      try {
        return await fetchCurrentUser();
      } catch (err: unknown) {
        // If 401 or not authenticated, return null instead of throwing
        if (typeof err === 'object' && err !== null && 'status' in err && (err as { status: number }).status === 401) {
          return null;
        }
        throw err;
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      loginUser(email, password),
    onSuccess: (newUser) => {
      queryClient.setQueryData(['auth', 'me'], newUser);
    },
  });

  const registerMutation = useMutation({
    mutationFn: async ({
      displayName,
      email,
      password,
    }: {
      displayName: string;
      email: string;
      password: string;
    }) => {
      await registerUser(displayName, email, password);
      return await loginUser(email, password);
    },
    onSuccess: (newUser) => {
      queryClient.setQueryData(['auth', 'me'], newUser);
    },
  });

  const logoutMutation = useMutation({
    mutationFn: logoutUser,
    onSuccess: () => {
      queryClient.setQueryData(['auth', 'me'], null);
      queryClient.clear();
    },
  });

  return {
    user: user ?? null,
    isLoading,
    isError,
    error,
    isAuthenticated: !!user,
    refetchUser: refetch,
    login: loginMutation.mutateAsync,
    register: registerMutation.mutateAsync,
    logout: logoutMutation.mutateAsync,
    isLoggingIn: loginMutation.isPending,
    isRegistering: registerMutation.isPending,
    isLoggingOut: logoutMutation.isPending,
  };
}

