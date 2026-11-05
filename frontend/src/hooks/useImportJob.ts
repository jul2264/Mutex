import { useQuery } from '@tanstack/react-query';
import { getJobStatus } from '../api/imports';
import type { ImportJobStatusResponse } from '../api/types';

const TERMINAL_STATUSES: Set<string> = new Set(['VALIDATED', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED']);

export function useImportJob(datasetId?: string, jobId?: string) {
  return useQuery<ImportJobStatusResponse>({
    queryKey: ['importJob', datasetId, jobId],
    queryFn: () => {
      if (!datasetId || !jobId) throw new Error('Missing datasetId or jobId');
      return getJobStatus(datasetId, jobId);
    },
    enabled: Boolean(datasetId && jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status || !TERMINAL_STATUSES.has(status)) {
        // Poll every 1s while in PARSING
        return 1000;
      }
      return false; // Stop polling when terminal
    },
  });
}

