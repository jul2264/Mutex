import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getPreview, confirmImport, cancelImport } from '../api/imports';
import { getDataset } from '../api/datasets';
import { useImportJob } from '../hooks/useImportJob';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/apiFetch';
import { Logo } from '../components/common/Logo';
import { ThemeToggle } from '../components/common/ThemeToggle';
import { Loader2, AlertCircle, FileCheck, RefreshCw } from 'lucide-react';

type TabStatus = 'ALL' | 'VALID' | 'INVALID' | 'DUPLICATE_CANDIDATE';

export const PreviewPage: React.FC = () => {
  const { datasetId } = useParams<{ datasetId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, logout, isLoggingOut } = useAuth();

  const queryJobId = searchParams.get('jobId') || undefined;

  const [activeTab, setActiveTab] = useState<TabStatus>('ALL');
  const [page, setPage] = useState<number>(0);
  const [includeDuplicates, setIncludeDuplicates] = useState<boolean>(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Fetch Dataset Details
  const { data: dataset, isLoading: datasetLoading } = useQuery({
    queryKey: ['dataset', datasetId],
    queryFn: () => getDataset(datasetId!),
    enabled: !!datasetId,
  });

  const latestJob = dataset?.latestImport;
  const targetJobId = queryJobId || latestJob?.id;

  // Poll Job Status if targetJobId is present
  const { data: jobStatus } = useImportJob(datasetId, targetJobId);

  const isTargetJobParsing = Boolean(targetJobId && (!jobStatus || jobStatus.status === 'PARSING'));
  const isDatasetParsing = Boolean(!targetJobId && dataset?.latestImport?.status === 'PARSING');
  const isParsing = isTargetJobParsing || isDatasetParsing;
  const isFailed = jobStatus?.status === 'FAILED';

  // Automatically invalidate preview and dataset when job reaches VALIDATED
  useEffect(() => {
    if (jobStatus?.status === 'VALIDATED') {
      queryClient.invalidateQueries({ queryKey: ['preview', datasetId] });
      queryClient.invalidateQueries({ queryKey: ['dataset', datasetId] });
    }
  }, [jobStatus?.status, datasetId, queryClient]);

  const isReadyToPreview = Boolean(
    datasetId &&
      !isParsing &&
      !isFailed &&
      (targetJobId ? (jobStatus?.status === 'VALIDATED' || jobStatus?.status === 'COMPLETED') : !datasetLoading)
  );

  // Fetch Preview Rows (disabled while parsing or before job status is validated)
  const {
    data: preview,
    isLoading: previewLoading,
  } = useQuery({
    queryKey: ['preview', datasetId, targetJobId, activeTab, page],
    queryFn: () =>
      getPreview(datasetId!, {
        jobId: targetJobId,
        status: activeTab,
        page,
        size: 50,
      }),
    enabled: isReadyToPreview,
    staleTime: 0,
  });

  // Confirm Import Mutation
  const confirmMutation = useMutation({
    mutationFn: () => {
      const jobToConfirm = targetJobId || latestJob?.id;
      if (!jobToConfirm) throw new Error('No import job found to confirm');
      return confirmImport(datasetId!, {
        importJobId: jobToConfirm,
        includeDuplicates,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataset', datasetId] });
      queryClient.invalidateQueries({ queryKey: ['preview', datasetId] });
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
      navigate(`/datasets/${datasetId}/analytics`);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setActionError(err.detail || `Failed to confirm import: ${err.code}`);
      } else {
        setActionError('Failed to confirm import. Please try again.');
      }
    },
  });

  // Cancel Import Mutation
  const cancelMutation = useMutation({
    mutationFn: () => {
      const jobToCancel = targetJobId || latestJob?.id;
      if (!jobToCancel) throw new Error('No import job found to cancel');
      return cancelImport(datasetId!, jobToCancel);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
      navigate('/');
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setActionError(err.detail || `Failed to cancel import: ${err.code}`);
      } else {
        setActionError('Failed to cancel import. Please try again.');
      }
    },
  });

  const counts = {
    total: jobStatus?.totalRows ?? preview?.counts?.total ?? latestJob?.totalRows ?? 0,
    valid: jobStatus?.validRows ?? preview?.counts?.valid ?? latestJob?.validRows ?? 0,
    invalid: jobStatus?.invalidRows ?? preview?.counts?.invalid ?? latestJob?.invalidRows ?? 0,
    duplicateCandidate:
      jobStatus?.duplicateRows ?? preview?.counts?.duplicateCandidate ?? latestJob?.duplicateRows ?? 0,
    warnings: jobStatus?.warningCount ?? preview?.counts?.warnings ?? 0,
  };

  const isStaged = preview?.source === 'STAGED' || (!preview && (jobStatus?.status === 'VALIDATED' || isParsing));
  const canConfirm =
    (preview?.source === 'STAGED' || jobStatus?.status === 'VALIDATED') &&
    counts.valid > 0 &&
    !confirmMutation.isPending &&
    !isParsing;

  return (
    <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white transition-colors">
      {/* Header */}
      <header className="border-b border-black/10 dark:border-white/10 bg-white dark:bg-[#0f0f10] sticky top-0 z-30 shadow-sm transition-colors">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Logo />
            <span className="text-sm font-semibold text-[#555] dark:text-[#a1a1aa] border-l-2 border-black/10 dark:border-white/10 pl-4 py-0.5">
              Data Preview & Review
            </span>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold px-3.5 py-1.5 rounded-full bg-[#f6f5f4] dark:bg-[#18181b] border border-black/15 dark:border-white/15 text-[#222] dark:text-[#f4f4f5]">
              Dataset: <span className="font-bold text-black dark:text-white">{dataset?.name ?? 'Loading...'}</span>
            </span>

            <ThemeToggle />

            {user && (
              <div className="flex items-center gap-3 border-l-2 border-black/10 dark:border-white/10 pl-3">
                <span className="text-sm text-[#111] dark:text-[#f4f4f5] font-semibold hidden sm:inline">
                  {user.displayName}
                </span>
                <button
                  onClick={() => logout()}
                  disabled={isLoggingOut}
                  className="text-xs text-[#666] dark:text-[#a1a1aa] hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30 px-3 py-1.5 rounded-lg border border-black/10 dark:border-white/15 font-semibold transition-colors cursor-pointer"
                >
                  {isLoggingOut ? 'Logging out...' : 'Sign out'}
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-8">
        {/* Navigation Breadcrumb */}
        <div className="mb-4 flex items-center gap-2 text-xs text-[#757575] dark:text-[#a1a1aa]">
          <Link to="/" className="hover:underline text-[#0075de] dark:text-[#38bdf8]">
            Datasets
          </Link>
          <span>/</span>
          <span className="text-black dark:text-white font-medium">{dataset?.name ?? '...'}</span>
          <span>/</span>
          <span>Preview</span>
        </div>

        {/* Title and Action Buttons */}
        <div className="mb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
              Review & Quality Inspection
            </h1>
            <p className="text-sm text-[#757575] dark:text-[#a1a1aa] mt-1">
              Source:{' '}
              <span className="font-semibold text-black dark:text-white">
                {preview?.source ?? (isStaged ? 'STAGED' : '...')}
              </span>{' '}
              · Inspect rows, resolve duplicates, and confirm commit to analytics.
            </p>
          </div>

          {isStaged && !isParsing && !isFailed && (
            <div className="flex flex-wrap items-center gap-3">
              {counts.duplicateCandidate > 0 && (
                <label className="flex items-center gap-2 text-xs text-[#37352f] dark:text-[#e4e4e7] bg-white dark:bg-[#18181b] px-3 py-2 rounded-lg border border-black/10 dark:border-white/10 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={includeDuplicates}
                    onChange={(e) => setIncludeDuplicates(e.target.checked)}
                    className="rounded border-black/20 dark:border-white/20 text-[#0075de] focus:ring-[#0075de]"
                  />
                  <span>Include {counts.duplicateCandidate} duplicate(s)</span>
                </label>
              )}

              {counts.invalid > 0 && targetJobId && (
                <a
                  href={`/api/datasets/${datasetId}/imports/${targetJobId}/errors.csv`}
                  download="errors.csv"
                  className="px-3 py-2 text-xs font-medium text-[#c92a2a] dark:text-red-400 bg-red-50 dark:bg-red-950/40 hover:bg-red-100 dark:hover:bg-red-900/50 border border-red-200 dark:border-red-900/60 rounded-lg transition-colors flex items-center gap-1.5"
                >
                  Download Error CSV ({counts.invalid})
                </a>
              )}

              <button
                onClick={() => {
                  if (window.confirm('Are you sure you want to cancel this import? Staged data will be discarded.')) {
                    cancelMutation.mutate();
                  }
                }}
                disabled={cancelMutation.isPending}
                className="px-3.5 py-2 text-xs font-medium text-[#757575] dark:text-[#a1a1aa] hover:text-red-700 dark:hover:text-red-400 bg-white dark:bg-[#18181b] hover:bg-red-50 dark:hover:bg-red-950/30 border border-black/10 dark:border-white/10 hover:border-red-200 dark:hover:border-red-900/50 rounded-lg transition-colors"
              >
                {cancelMutation.isPending ? 'Cancelling...' : 'Cancel Import'}
              </button>

              <button
                onClick={() => confirmMutation.mutate()}
                disabled={!canConfirm || confirmMutation.isPending}
                className="px-4 py-2 text-xs font-semibold text-white bg-[#0075de] hover:bg-[#0060b8] disabled:opacity-50 disabled:cursor-not-allowed rounded-lg shadow-xs transition-colors flex items-center gap-1.5 cursor-pointer"
              >
                {confirmMutation.isPending ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Committing...</span>
                  </>
                ) : (
                  <>
                    <FileCheck className="w-3.5 h-3.5" />
                    <span>Confirm & Commit Data</span>
                  </>
                )}
              </button>
            </div>
          )}
        </div>

        {actionError && (
          <div
            role="alert"
            className="mb-6 p-4 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 text-xs text-red-700 dark:text-red-300 flex items-center justify-between"
          >
            <span>{actionError}</span>
            <button onClick={() => setActionError(null)} className="font-bold ml-4 hover:opacity-75">
              ✕
            </button>
          </div>
        )}

        {/* Tab Filters */}
        <div className="flex border-b border-black/10 dark:border-white/10 gap-2 mb-6">
          <button
            onClick={() => {
              setActiveTab('ALL');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 cursor-pointer ${
              activeTab === 'ALL'
                ? 'border-[#0075de] text-[#0075de] dark:border-[#38bdf8] dark:text-[#38bdf8]'
                : 'border-transparent text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white'
            }`}
          >
            All Rows
            <span className="px-1.5 py-0.5 rounded-full bg-black/5 dark:bg-white/10 text-[10px] font-semibold text-black dark:text-white">
              {counts.total}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('VALID');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 cursor-pointer ${
              activeTab === 'VALID'
                ? 'border-emerald-600 text-emerald-700 dark:border-emerald-400 dark:text-emerald-400'
                : 'border-transparent text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white'
            }`}
          >
            Valid
            <span className="px-1.5 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border border-emerald-200/60 dark:border-emerald-800/40 text-[10px] font-semibold">
              {counts.valid}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('INVALID');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 cursor-pointer ${
              activeTab === 'INVALID'
                ? 'border-red-600 text-red-700 dark:border-red-400 dark:text-red-400'
                : 'border-transparent text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white'
            }`}
          >
            Invalid
            <span className="px-1.5 py-0.5 rounded-full bg-red-50 dark:bg-red-950/50 text-red-700 dark:text-red-300 border border-red-200/60 dark:border-red-800/40 text-[10px] font-semibold">
              {counts.invalid}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('DUPLICATE_CANDIDATE');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 cursor-pointer ${
              activeTab === 'DUPLICATE_CANDIDATE'
                ? 'border-amber-600 text-amber-700 dark:border-amber-400 dark:text-amber-400'
                : 'border-transparent text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white'
            }`}
          >
            Duplicates
            <span className="px-1.5 py-0.5 rounded-full bg-amber-50 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 border border-amber-200/60 dark:border-amber-800/40 text-[10px] font-semibold">
              {counts.duplicateCandidate}
            </span>
          </button>
        </div>

        {/* Table / Status Container */}
        <div className="bg-white dark:bg-[#0f0f10] border border-black/8 dark:border-white/10 rounded-xl shadow-xs overflow-hidden transition-colors">
          {/* Active Parsing View */}
          {isParsing ? (
            <div className="py-20 flex flex-col items-center justify-center text-center px-4">
              <div className="relative mb-5 flex items-center justify-center">
                <div className="w-14 h-14 rounded-full border-3 border-[#0075de]/20 dark:border-[#38bdf8]/20 border-t-[#0075de] dark:border-t-[#38bdf8] animate-spin" />
                <RefreshCw className="w-6 h-6 text-[#0075de] dark:text-[#38bdf8] absolute animate-pulse" />
              </div>
              <h2 className="text-lg font-bold text-black dark:text-white">
                Parsing & Validating Spreadsheet...
              </h2>
              <p className="text-xs text-[#757575] dark:text-[#a1a1aa] mt-1.5 max-w-md">
                Mutex is validating schema conformity, checking business constraints, and indexing rows for quality inspection.
              </p>
              {jobStatus && jobStatus.rowsProcessed > 0 && (
                <div className="mt-4 px-3.5 py-1.5 rounded-full bg-[#0075de]/10 dark:bg-[#38bdf8]/15 border border-[#0075de]/20 dark:border-[#38bdf8]/30 text-[#0075de] dark:text-[#38bdf8] text-xs font-semibold">
                  {jobStatus.rowsProcessed.toLocaleString()} rows processed so far
                </div>
              )}
            </div>
          ) : isFailed ? (
            /* Failed Ingestion View */
            <div className="py-16 flex flex-col items-center justify-center text-center px-4">
              <div className="w-14 h-14 rounded-full bg-red-100 dark:bg-red-950/50 text-red-600 dark:text-red-400 flex items-center justify-center mb-4">
                <AlertCircle className="w-8 h-8" />
              </div>
              <h2 className="text-lg font-bold text-red-600 dark:text-red-400">
                Spreadsheet Ingestion Failed
              </h2>
              <p className="text-xs text-[#757575] dark:text-[#a1a1aa] mt-2 max-w-md">
                {jobStatus?.fileError?.message || 'The uploaded file could not be parsed or validated.'}
              </p>
              <div className="mt-6 flex items-center gap-3">
                <Link
                  to="/datasets/upload"
                  className="px-4 py-2 bg-[#0075de] hover:bg-[#0060b8] text-white text-xs font-semibold rounded-lg transition-colors"
                >
                  Upload Another File
                </Link>
                <Link
                  to="/"
                  className="px-4 py-2 bg-white dark:bg-[#18181b] border border-black/10 dark:border-white/15 text-xs font-semibold text-black dark:text-white rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
                >
                  Back to Datasets
                </Link>
              </div>
            </div>
          ) : previewLoading || datasetLoading ? (
            /* Loading Preview View */
            <div className="py-20 flex flex-col items-center justify-center text-center px-4">
              <Loader2 className="w-8 h-8 text-[#0075de] animate-spin mb-3" />
              <div className="text-xs text-[#757575] dark:text-[#a1a1aa]">Loading preview rows...</div>
            </div>
          ) : !preview || preview.content.length === 0 ? (
            /* Empty Table View */
            <div className="py-20 text-center text-xs text-[#757575] dark:text-[#a1a1aa]">
              No rows found in category <strong className="text-black dark:text-white">{activeTab}</strong>.
            </div>
          ) : (
            /* Data Table View */
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[#fcfbf9] dark:bg-[#18181b] border-b border-black/8 dark:border-white/10 text-[#757575] dark:text-[#a1a1aa] uppercase text-[10px] font-semibold tracking-wider">
                  <tr>
                    <th className="py-3 px-4 w-12">#</th>
                    <th className="py-3 px-4 w-28">Status</th>
                    <th className="py-3 px-4">Order ID</th>
                    <th className="py-3 px-4">Order Date</th>
                    <th className="py-3 px-4">Product Name</th>
                    <th className="py-3 px-4">Category</th>
                    <th className="py-3 px-4">Region</th>
                    <th className="py-3 px-4 text-right">Qty</th>
                    <th className="py-3 px-4 text-right">Unit Price</th>
                    <th className="py-3 px-4 text-right">Unit Cost</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-black/5 dark:divide-white/5">
                  {preview.content.map((row) => {
                    const isInvalid = row.status === 'INVALID';
                    const isDuplicate = row.status === 'DUPLICATE_CANDIDATE';

                    return (
                      <React.Fragment key={row.rowNumber}>
                        <tr
                          className={`hover:bg-[#f9f8f6] dark:hover:bg-[#18181b]/60 transition-colors ${
                            isInvalid
                              ? 'bg-red-50/30 dark:bg-red-950/20'
                              : isDuplicate
                              ? 'bg-amber-50/20 dark:bg-amber-950/20'
                              : ''
                          }`}
                        >
                          <td className="py-3 px-4 text-[#757575] dark:text-[#a1a1aa] font-mono">{row.rowNumber}</td>
                          <td className="py-3 px-4">
                            {row.status === 'VALID' && (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/50">
                                VALID
                              </span>
                            )}
                            {row.status === 'INVALID' && (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-red-50 dark:bg-red-950/50 text-red-700 dark:text-red-300 border border-red-200 dark:border-red-800/50">
                                INVALID
                              </span>
                            )}
                            {row.status === 'DUPLICATE_CANDIDATE' && (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-amber-50 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800/50">
                                DUPLICATE
                              </span>
                            )}
                          </td>
                          <td className="py-3 px-4 font-mono font-medium text-[#37352f] dark:text-[#e4e4e7]">
                            {row.values.orderId ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-[#37352f] dark:text-[#e4e4e7]">
                            {row.values.orderDate ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 font-medium text-[#37352f] dark:text-[#e4e4e7]">
                            {row.values.productName ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-[#757575] dark:text-[#a1a1aa]">
                            {row.values.category ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-[#757575] dark:text-[#a1a1aa]">
                            {row.values.region ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-right font-mono text-[#37352f] dark:text-[#e4e4e7]">
                            {row.values.quantity ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-right font-mono text-[#37352f] dark:text-[#e4e4e7]">
                            {row.values.unitPrice ? `₹${row.values.unitPrice}` : <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-right font-mono text-[#37352f] dark:text-[#e4e4e7]">
                            {row.values.unitCost ? `₹${row.values.unitCost}` : <span className="text-red-500 italic">missing</span>}
                          </td>
                        </tr>

                        {/* Issue banner for rows with issues */}
                        {row.issues.length > 0 && (
                          <tr className={isInvalid ? 'bg-red-50/40 dark:bg-red-950/30' : 'bg-amber-50/30 dark:bg-amber-950/30'}>
                            <td colSpan={10} className="py-2 px-4 border-b border-black/5 dark:border-white/5">
                              <div className="flex flex-wrap gap-2 items-center">
                                {row.issues.map((issue, idx) => (
                                  <span
                                    key={idx}
                                    className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium ${
                                      issue.severity === 'ERROR'
                                        ? 'bg-red-100 dark:bg-red-950/60 text-red-800 dark:text-red-300 border border-red-200 dark:border-red-900/60'
                                        : 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border border-amber-200 dark:border-amber-900/60'
                                    }`}
                                  >
                                    <strong className="uppercase font-semibold text-[10px]">
                                      {issue.column}:
                                    </strong>
                                    <span>{issue.message}</span>
                                    {issue.rawValue && (
                                      <span className="opacity-75 font-mono text-[10px]">
                                        (raw: &quot;{issue.rawValue}&quot;)
                                      </span>
                                    )}
                                  </span>
                                ))}
                              </div>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination Controls */}
          {preview && preview.totalPages > 1 && !isParsing && (
            <div className="border-t border-black/8 dark:border-white/10 px-6 py-3 bg-[#fcfbf9] dark:bg-[#18181b] flex items-center justify-between text-xs text-[#757575] dark:text-[#a1a1aa] transition-colors">
              <span>
                Showing {page * preview.size + 1}–{Math.min((page + 1) * preview.size, Number(preview.totalElements))} of {preview.totalElements} rows
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={page === 0}
                  className="px-3 py-1.5 bg-white dark:bg-[#0f0f10] border border-black/10 dark:border-white/15 rounded-lg hover:bg-black/5 dark:hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-medium text-black dark:text-white cursor-pointer"
                >
                  Previous
                </button>
                <span>
                  Page {page + 1} of {preview.totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(p + 1, preview.totalPages - 1))}
                  disabled={page >= preview.totalPages - 1}
                  className="px-3 py-1.5 bg-white dark:bg-[#0f0f10] border border-black/10 dark:border-white/15 rounded-lg hover:bg-black/5 dark:hover:bg-white/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-medium text-black dark:text-white cursor-pointer"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
