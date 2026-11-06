import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getDataset, renameDataset, deleteDataset } from '../api/datasets';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/apiFetch';
import { formatDate } from '../lib/format';
import {
  ArrowLeft,
  BarChart3,
  Calendar,
  Layers,
  Clock,
  Trash2,
  Edit2,
  AlertCircle,
  CheckCircle,
  XCircle,
  Upload,
} from 'lucide-react';
import { Logo } from '../components/common/Logo';
import { ThemeToggle } from '../components/common/ThemeToggle';

export const DatasetDetailPage: React.FC = () => {
  const { datasetId } = useParams<{ datasetId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, logout, isLoggingOut } = useAuth();

  const [isRenaming, setIsRenaming] = useState<boolean>(false);
  const [renameInput, setRenameInput] = useState<string>('');
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState<boolean>(false);
  const [deleteConfirmInput, setDeleteConfirmInput] = useState<string>('');
  const [errorBanner, setErrorBanner] = useState<string | null>(null);

  const {
    data: dataset,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['dataset', datasetId],
    queryFn: () => {
      if (!datasetId) throw new Error('Dataset ID is required');
      return getDataset(datasetId);
    },
    enabled: Boolean(datasetId),
  });

  const renameMutation = useMutation({
    mutationFn: (newName: string) => {
      if (!datasetId) throw new Error('No dataset ID');
      return renameDataset(datasetId, newName);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataset', datasetId] });
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
      setIsRenaming(false);
      setErrorBanner(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setErrorBanner(err.detail || `Rename failed: ${err.code}`);
      } else {
        setErrorBanner('Failed to rename dataset.');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => {
      if (!datasetId) throw new Error('No dataset ID');
      return deleteDataset(datasetId, deleteConfirmInput.trim());
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
      navigate('/');
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setErrorBanner(err.detail || `Delete failed: ${err.code}`);
      } else {
        setErrorBanner('Failed to delete dataset.');
      }
    },
  });

  const handleStartRename = () => {
    if (dataset) {
      setRenameInput(dataset.name);
      setIsRenaming(true);
      setErrorBanner(null);
    }
  };

  const handleSaveRename = (e: React.FormEvent) => {
    e.preventDefault();
    if (renameInput.trim()) {
      renameMutation.mutate(renameInput.trim());
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white flex items-center justify-center transition-colors">
        <div className="flex items-center gap-2 text-sm text-[#757575] dark:text-[#a1a1aa]">
          <div className="w-4 h-4 rounded-full border-2 border-[#0075de] border-t-transparent animate-spin" />
          Loading dataset details...
        </div>
      </div>
    );
  }

  if (isError || !dataset) {
    return (
      <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white p-8 max-w-4xl mx-auto transition-colors">
        <div className="p-6 bg-white dark:bg-[#0f0f10] rounded-xl border border-black/8 dark:border-white/10 text-center">
          <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-3" />
          <h2 className="text-lg font-bold text-black dark:text-white mb-1">Dataset Not Found</h2>
          <p className="text-sm text-[#757575] dark:text-[#a1a1aa] mb-4">
            {error instanceof Error ? error.message : 'The requested dataset could not be loaded.'}
          </p>
          <Link to="/" className="px-4 py-2 bg-[#0075de] text-white text-sm font-semibold rounded-lg">
            Back to Datasets
          </Link>
        </div>
      </div>
    );
  }

  const importHistory = dataset.importHistory ?? [];

  return (
    <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white transition-colors">
      {/* Top Header */}
      <header className="border-b border-black/10 dark:border-white/10 bg-white dark:bg-[#0f0f10] sticky top-0 z-30 shadow-sm transition-colors">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Logo />
            <span className="text-sm font-semibold text-[#555] dark:text-[#a1a1aa] border-l-2 border-black/10 dark:border-white/10 pl-4 py-0.5">
              Dataset Settings
            </span>
          </div>

          <div className="flex items-center gap-3">
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

      {/* Main Container */}
      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-xs text-[#757575] hover:text-black transition-colors mb-3"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Datasets
          </Link>

          {errorBanner && (
            <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm flex items-center gap-3">
              <AlertCircle className="w-5 h-5 shrink-0 text-red-600" />
              <span>{errorBanner}</span>
            </div>
          )}

          {/* Title & Actions Row */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              {isRenaming ? (
                <form onSubmit={handleSaveRename} className="flex items-center gap-2">
                  <input
                    type="text"
                    value={renameInput}
                    onChange={(e) => setRenameInput(e.target.value)}
                    maxLength={100}
                    className="px-3 py-1.5 text-xl font-bold bg-white border border-[#0075de] rounded-lg focus:outline-none"
                    autoFocus
                  />
                  <button
                    type="submit"
                    disabled={renameMutation.isPending || !renameInput.trim()}
                    className="px-3 py-1.5 bg-[#0075de] text-white text-xs font-semibold rounded-lg hover:bg-[#0060b8]"
                  >
                    Save
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsRenaming(false)}
                    className="px-3 py-1.5 bg-black/5 text-[#757575] text-xs font-semibold rounded-lg hover:text-black"
                  >
                    Cancel
                  </button>
                </form>
              ) : (
                <div className="flex items-center gap-3">
                  <h1 className="text-2xl font-bold tracking-tight text-black">{dataset.name}</h1>
                  <button
                    onClick={handleStartRename}
                    className="p-1.5 text-[#757575] hover:text-black hover:bg-black/5 rounded-md transition-colors"
                    title="Rename dataset"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                </div>
              )}
              <p className="text-xs text-[#757575] mt-1">
                ID: <span className="font-mono text-[11px]">{dataset.id}</span> · Currency: <strong className="text-black">{dataset.currencyCode}</strong>
              </p>
            </div>

            <div className="flex items-center gap-3">
              <Link
                to={`/datasets/${dataset.id}/analytics`}
                className="px-4 py-2 bg-[#0075de] hover:bg-[#0060b8] text-white text-xs font-semibold rounded-lg shadow-xs flex items-center gap-2 transition-colors"
              >
                <BarChart3 className="w-4 h-4" />
                Open Analytics
              </Link>
              <button
                onClick={() => setIsDeleteDialogOpen(true)}
                className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors border border-red-200"
                title="Delete dataset"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Metadata Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <div className="p-4 rounded-xl bg-white border border-black/8 shadow-xs">
            <span className="text-xs font-semibold text-[#757575] uppercase tracking-wider block mb-1">Status</span>
            <div className="flex items-center gap-2 mt-1">
              <span
                className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium ${
                  dataset.status === 'ACTIVE'
                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                }`}
              >
                {dataset.status === 'ACTIVE' ? <CheckCircle className="w-3 h-3" /> : <Clock className="w-3 h-3" />}
                {dataset.status}
              </span>
              <span className="text-xs text-[#757575]">v{dataset.dataVersion}</span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-white border border-black/8 shadow-xs">
            <span className="text-xs font-semibold text-[#757575] uppercase tracking-wider block mb-1">Rows Committed</span>
            <div className="flex items-center gap-2 text-xl font-bold text-black mt-0.5">
              <Layers className="w-5 h-5 text-[#0075de]" />
              {dataset.rowCount.toLocaleString()}
            </div>
          </div>

          <div className="p-4 rounded-xl bg-white border border-black/8 shadow-xs">
            <span className="text-xs font-semibold text-[#757575] uppercase tracking-wider block mb-1">Date Range</span>
            <div className="flex items-center gap-1.5 text-xs font-medium text-black mt-2">
              <Calendar className="w-4 h-4 text-[#757575]" />
              {dataset.minOrderDate && dataset.maxOrderDate ? (
                <span>
                  {formatDate(dataset.minOrderDate)} → {formatDate(dataset.maxOrderDate)}
                </span>
              ) : (
                <span className="text-[#757575]">No date range</span>
              )}
            </div>
          </div>

          <div className="p-4 rounded-xl bg-white border border-black/8 shadow-xs">
            <span className="text-xs font-semibold text-[#757575] uppercase tracking-wider block mb-1">Created</span>
            <div className="flex items-center gap-1.5 text-xs text-[#757575] mt-2">
              <Clock className="w-4 h-4" />
              {new Date(dataset.createdAt).toLocaleDateString()}
            </div>
          </div>
        </div>

        {/* Import History Table */}
        <div className="bg-white rounded-xl border border-black/8 shadow-xs overflow-hidden">
          <div className="p-5 border-b border-black/8 flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-black">Import History</h2>
              <p className="text-xs text-[#757575] mt-0.5">Audit log of all uploads and ingestion jobs for this dataset.</p>
            </div>
            <Link
              to="/datasets/upload"
              className="px-3 py-1.5 bg-black/5 hover:bg-black/10 text-xs font-semibold text-black rounded-lg flex items-center gap-1.5 transition-colors"
            >
              <Upload className="w-3.5 h-3.5" />
              New Upload
            </Link>
          </div>

          {importHistory.length === 0 ? (
            <div className="p-8 text-center text-sm text-[#757575]">
              No imports recorded for this dataset.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="bg-[#faf9f8] border-b border-black/8 text-[#757575] font-semibold uppercase tracking-wider">
                    <th className="py-3 px-4">Filename</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Policy</th>
                    <th className="py-3 px-4 text-right">Total Rows</th>
                    <th className="py-3 px-4 text-right">Valid</th>
                    <th className="py-3 px-4 text-right">Invalid</th>
                    <th className="py-3 px-4 text-right">Duplicates</th>
                    <th className="py-3 px-4">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-black/5">
                  {importHistory.map((job) => (
                    <tr key={job.id} className="hover:bg-black/[0.02] transition-colors">
                      <td className="py-3 px-4 font-medium text-black flex items-center gap-2">
                        {job.originalFilename}
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium ${
                            job.status === 'COMPLETED'
                              ? 'bg-emerald-50 text-emerald-700'
                              : job.status === 'FAILED'
                              ? 'bg-red-50 text-red-700'
                              : 'bg-amber-50 text-amber-700'
                          }`}
                        >
                          {job.status === 'COMPLETED' && <CheckCircle className="w-3 h-3" />}
                          {job.status === 'FAILED' && <XCircle className="w-3 h-3" />}
                          {job.status}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-[#757575]">{job.policy}</td>
                      <td className="py-3 px-4 text-right font-mono">{job.totalRows.toLocaleString()}</td>
                      <td className="py-3 px-4 text-right font-mono text-emerald-600">{job.validRows.toLocaleString()}</td>
                      <td className="py-3 px-4 text-right font-mono text-red-600">{job.invalidRows.toLocaleString()}</td>
                      <td className="py-3 px-4 text-right font-mono text-amber-600">{job.duplicateRows.toLocaleString()}</td>
                      <td className="py-3 px-4 text-[#757575]">{new Date(job.createdAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>

      {/* Delete Confirmation Modal */}
      {isDeleteDialogOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-xl border border-black/15 shadow-xl max-w-md w-full p-6 animate-in fade-in zoom-in-95 duration-150">
            <h3 className="text-lg font-bold text-black mb-2 flex items-center gap-2 text-red-600">
              <AlertCircle className="w-5 h-5" />
              Delete Dataset
            </h3>
            <p className="text-sm text-[#757575] mb-4">
              This action is permanent and will cascade-delete all sales records, import history, and staging data for{' '}
              <strong className="text-black">{dataset.name}</strong>.
            </p>
            <div className="mb-4">
              <label htmlFor="confirm-delete-input" className="block text-xs font-semibold text-[#37352f] mb-1.5">
                Type <strong className="text-black font-mono">{dataset.name}</strong> to confirm:
              </label>
              <input
                id="confirm-delete-input"
                type="text"
                value={deleteConfirmInput}
                onChange={(e) => setDeleteConfirmInput(e.target.value)}
                placeholder={dataset.name}
                className="w-full px-3 py-2 border border-black/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-red-500/30 focus:border-red-500"
              />
            </div>
            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => {
                  setIsDeleteDialogOpen(false);
                  setDeleteConfirmInput('');
                }}
                className="px-4 py-2 text-sm text-[#757575] hover:text-black hover:bg-black/5 rounded-lg"
              >
                Cancel
              </button>
              <button
                onClick={() => deleteMutation.mutate()}
                disabled={deleteConfirmInput !== dataset.name || deleteMutation.isPending}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 disabled:opacity-40 text-white text-sm font-semibold rounded-lg shadow-xs"
              >
                {deleteMutation.isPending ? 'Deleting...' : 'Delete permanently'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

