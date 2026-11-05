import { useQuery } from '@tanstack/react-query';
import { fetchSummary } from '../api/analytics';
import type { AnalyticsFilterParams } from '../api/types';

export function useSummary(datasetId: string, filter?: AnalyticsFilterParams) {
  return useQuery({
    queryKey: ['analytics', 'summary', datasetId, filter],
    queryFn: ({ signal }) => fetchSummary(datasetId, filter, signal),
    enabled: Boolean(datasetId),
  });
}
