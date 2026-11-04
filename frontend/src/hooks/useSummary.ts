import { useQuery } from '@tanstack/react-query';
import { fetchSummary } from '../api/analytics';

export function useSummary(datasetId: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ['analytics', 'summary', datasetId, from, to],
    queryFn: ({ signal }) => fetchSummary(datasetId, from, to, signal),
    enabled: Boolean(datasetId),
  });
}

