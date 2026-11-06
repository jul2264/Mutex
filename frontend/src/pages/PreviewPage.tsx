import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getPreview, confirmImport, cancelImport } from '../api/imports';
import { getDataset } from '../api/datasets';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/apiFetch';

type TabStatus = 'ALL' | 'VALID' | 'INVALID' | 'DUPLICATE_CANDIDATE';

export const PreviewPage: React.FC = () => {
  const { datasetId } = useParams<{ datasetId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, logout, isLoggingOut } = useAuth();

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

  // Fetch Preview Rows
  const { data: preview, isLoading: previewLoading } = useQuery({
    queryKey: ['preview', datasetId, activeTab, page],
    queryFn: () =>
      getPreview(datasetId!, {
        status: activeTab,
        page,
        size: 50,
      }),
    enabled: !!datasetId,
  });

  const latestJob = dataset?.latestImport;

  // Confirm Import Mutation
  const confirmMutation = useMutation({
    mutationFn: () => {
      if (!latestJob?.id) throw new Error('No import job found to confirm');
      return confirmImport(datasetId!, {
        importJobId: latestJob.id,
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
      if (!latestJob?.id) throw new Error('No import job found to cancel');
      return cancelImport(datasetId!, latestJob.id);
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

  const counts = preview?.counts ?? {
    total: 0,
    valid: 0,
    invalid: 0,
    duplicateCandidate: 0,
    warnings: 0,
  };

  const isStaged = preview?.source === 'STAGED';
  const canConfirm = isStaged && counts.valid > 0 && !confirmMutation.isPending;

  return (
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Header */}
      <header className="border-b border-black/10 bg-white sticky top-0 z-30 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link to="/" className="font-bold text-2xl tracking-tight text-black flex items-center gap-3 hover:opacity-85 transition-opacity">
              <span className="w-10 h-10 rounded-xl bg-[#0075de] text-white text-base font-extrabold flex items-center justify-center shadow-sm">
                μ
              </span>
              Mutex
            </Link>
            <span className="text-sm font-semibold text-[#555] border-l-2 border-black/10 pl-4 py-0.5">
              Data Preview & Review
            </span>
          </div>

          <div className="flex items-center gap-4">
            <span className="text-xs font-semibold px-3.5 py-1.5 rounded-full bg-[#f6f5f4] border border-black/15 text-[#222]">
              Dataset: <span className="font-bold text-black">{dataset?.name ?? 'Loading...'}</span>
            </span>

            {user && (
              <div className="flex items-center gap-3 border-l-2 border-black/10 pl-4">
                <span className="text-sm text-[#111] font-semibold hidden sm:inline">
                  {user.displayName}
                </span>
                <button
                  onClick={() => logout()}
                  disabled={isLoggingOut}
                  className="text-xs text-[#666] hover:text-red-700 hover:bg-red-50 px-3 py-1.5 rounded-lg border border-black/10 font-semibold transition-colors cursor-pointer"
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
        <div className="mb-4 flex items-center gap-2 text-xs text-[#757575]">
          <Link to="/" className="hover:underline text-[#0075de]">
            Datasets
          </Link>
          <span>/</span>
          <span className="text-black font-medium">{dataset?.name}</span>
          <span>/</span>
          <span>Preview</span>
        </div>

        {/* Title and Action Buttons */}
        <div className="mb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-black">
              Review & Quality Inspection
            </h1>
            <p className="text-sm text-[#757575] mt-1">
              Source: <span className="font-medium text-black">{preview?.source ?? '...'}</span> · Inspect rows, resolve duplicates, and confirm commit to analytics.
            </p>
          </div>

          {isStaged && (
            <div className="flex flex-wrap items-center gap-3">
              {counts.duplicateCandidate > 0 && (
                <label className="flex items-center gap-2 text-xs text-[#37352f] bg-white px-3 py-2 rounded-lg border border-black/8 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={includeDuplicates}
                    onChange={(e) => setIncludeDuplicates(e.target.checked)}
                    className="rounded border-black/20 text-[#0075de] focus:ring-[#0075de]"
                  />
                  <span>Include {counts.duplicateCandidate} duplicate(s)</span>
                </label>
              )}

              {counts.invalid > 0 && latestJob?.id && (
                <a
                  href={`/api/datasets/${datasetId}/imports/${latestJob.id}/errors.csv`}
                  download="errors.csv"
                  className="px-3 py-2 text-xs font-medium text-[#c92a2a] bg-red-50 hover:bg-red-100 border border-red-200 rounded-lg transition-colors flex items-center gap-1.5"
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
                className="px-3.5 py-2 text-xs font-medium text-[#757575] hover:text-red-700 bg-white hover:bg-red-50 border border-black/10 hover:border-red-200 rounded-lg transition-colors"
              >
                {cancelMutation.isPending ? 'Cancelling...' : 'Cancel Import'}
              </button>

              <button
                onClick={() => confirmMutation.mutate()}
                disabled={!canConfirm || confirmMutation.isPending}
                className="px-4 py-2 text-xs font-semibold text-white bg-[#0075de] hover:bg-[#0060b8] disabled:opacity-50 disabled:cursor-not-allowed rounded-lg shadow-xs transition-colors"
              >
                {confirmMutation.isPending ? 'Committing...' : 'Confirm & Commit Data'}
              </button>
            </div>
          )}
        </div>

        {actionError && (
          <div
            role="alert"
            className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-xs text-red-700 flex items-center justify-between"
          >
            <span>{actionError}</span>
            <button onClick={() => setActionError(null)} className="font-bold ml-4">
              ✕
            </button>
          </div>
        )}

        {/* Tab Filters */}
        <div className="flex border-b border-black/10 gap-2 mb-6">
          <button
            onClick={() => {
              setActiveTab('ALL');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'ALL'
                ? 'border-[#0075de] text-[#0075de]'
                : 'border-transparent text-[#757575] hover:text-black'
            }`}
          >
            All Rows
            <span className="px-1.5 py-0.5 rounded-full bg-black/5 text-[10px]">
              {counts.total}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('VALID');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'VALID'
                ? 'border-emerald-600 text-emerald-700'
                : 'border-transparent text-[#757575] hover:text-black'
            }`}
          >
            Valid
            <span className="px-1.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-semibold">
              {counts.valid}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('INVALID');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'INVALID'
                ? 'border-red-600 text-red-700'
                : 'border-transparent text-[#757575] hover:text-black'
            }`}
          >
            Invalid
            <span className="px-1.5 py-0.5 rounded-full bg-red-50 text-red-700 text-[10px] font-semibold">
              {counts.invalid}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('DUPLICATE_CANDIDATE');
              setPage(0);
            }}
            className={`pb-3 px-3 text-xs font-medium border-b-2 transition-colors flex items-center gap-1.5 ${
              activeTab === 'DUPLICATE_CANDIDATE'
                ? 'border-amber-600 text-amber-700'
                : 'border-transparent text-[#757575] hover:text-black'
            }`}
          >
            Duplicates
            <span className="px-1.5 py-0.5 rounded-full bg-amber-50 text-amber-700 text-[10px] font-semibold">
              {counts.duplicateCandidate}
            </span>
          </button>
        </div>

        {/* Table Container */}
        <div className="bg-white border border-black/8 rounded-xl shadow-xs overflow-hidden">
          {previewLoading || datasetLoading ? (
            <div className="py-20 text-center text-xs text-[#757575]">Loading preview data...</div>
          ) : !preview || preview.content.length === 0 ? (
            <div className="py-20 text-center text-xs text-[#757575]">
              No rows found in category <strong className="text-black">{activeTab}</strong>.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[#fcfbf9] border-b border-black/8 text-[#757575] uppercase text-[10px] font-semibold tracking-wider">
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
                <tbody className="divide-y divide-black/5">
                  {preview.content.map((row) => {
                    const isInvalid = row.status === 'INVALID';
                    const isDuplicate = row.status === 'DUPLICATE_CANDIDATE';

                    return (
                      <React.Fragment key={row.rowNumber}>
                        <tr
                          className={`hover:bg-[#f9f8f6] transition-colors ${
                            isInvalid ? 'bg-red-50/30' : isDuplicate ? 'bg-amber-50/20' : ''
                          }`}
                        >
                          <td className="py-3 px-4 text-[#757575] font-mono">{row.rowNumber}</td>
                          <td className="py-3 px-4">
                            {row.status === 'VALID' && (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                                VALID
                              </span>
                            )}
                            {row.status === 'INVALID' && (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-red-50 text-red-700 border border-red-200">
                                INVALID
                              </span>
                            )}
                            {row.status === 'DUPLICATE_CANDIDATE' && (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
                                DUPLICATE
                              </span>
                            )}
                          </td>
                          <td className="py-3 px-4 font-mono font-medium text-[#37352f]">
                            {row.values.orderId ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-[#37352f]">
                            {row.values.orderDate ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 font-medium text-[#37352f]">
                            {row.values.productName ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-[#757575]">
                            {row.values.category ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-[#757575]">
                            {row.values.region ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-right font-mono text-[#37352f]">
                            {row.values.quantity ?? <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-right font-mono text-[#37352f]">
                            {row.values.unitPrice ? `₹${row.values.unitPrice}` : <span className="text-red-500 italic">missing</span>}
                          </td>
                          <td className="py-3 px-4 text-right font-mono text-[#37352f]">
                            {row.values.unitCost ? `₹${row.values.unitCost}` : <span className="text-red-500 italic">missing</span>}
                          </td>
                        </tr>

                        {/* Issue banner for rows with issues */}
                        {row.issues.length > 0 && (
                          <tr className={isInvalid ? 'bg-red-50/40' : 'bg-amber-50/30'}>
                            <td colSpan={10} className="py-2 px-4 border-b border-black/5">
                              <div className="flex flex-wrap gap-2 items-center">
                                {row.issues.map((issue, idx) => (
                                  <span
                                    key={idx}
                                    className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium ${
                                      issue.severity === 'ERROR'
                                        ? 'bg-red-100 text-red-800 border border-red-200'
                                        : 'bg-amber-100 text-amber-800 border border-amber-200'
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
          {preview && preview.totalPages > 1 && (
            <div className="border-t border-black/8 px-6 py-3 bg-[#fcfbf9] flex items-center justify-between text-xs text-[#757575]">
              <span>
                Showing {page * preview.size + 1}–{Math.min((page + 1) * preview.size, Number(preview.totalElements))} of {preview.totalElements} rows
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={page === 0}
                  className="px-3 py-1.5 bg-white border border-black/10 rounded-lg hover:bg-black/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-medium text-black"
                >
                  Previous
                </button>
                <span>
                  Page {page + 1} of {preview.totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(p + 1, preview.totalPages - 1))}
                  disabled={page >= preview.totalPages - 1}
                  className="px-3 py-1.5 bg-white border border-black/10 rounded-lg hover:bg-black/5 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-medium text-black"
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
