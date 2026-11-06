import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listDatasets, renameDataset, deleteDataset } from '../api/datasets';
import { useAuth } from '../hooks/useAuth';
import type { DatasetResponse } from '../api/types';
import { ApiError } from '../api/apiFetch';
import { Upload, FileSpreadsheet, AlertCircle } from 'lucide-react';
import { Logo } from '../components/common/Logo';
import { ThemeToggle } from '../components/common/ThemeToggle';

export const DatasetListPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, logout, isLoggingOut } = useAuth();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [isWindowDragging, setIsWindowDragging] = useState<boolean>(false);
  const [dropError, setDropError] = useState<string | null>(null);

  const [page, setPage] = useState<number>(0);
  const [editingDataset, setEditingDataset] = useState<DatasetResponse | null>(null);
  const [renameInput, setRenameInput] = useState<string>('');
  const [deletingDataset, setDeletingDataset] = useState<DatasetResponse | null>(null);
  const [confirmDeleteInput, setConfirmDeleteInput] = useState<string>('');
  const [dialogError, setDialogError] = useState<string | null>(null);

  const handleFileSelected = useCallback((selectedFile: File) => {
    setDropError(null);
    const lower = selectedFile.name.toLowerCase();
    if (!lower.endsWith('.csv') && !lower.endsWith('.xlsx')) {
      setDropError('Invalid format: Please upload a .csv or .xlsx sales spreadsheet.');
      return;
    }
    if (selectedFile.size > 10 * 1024 * 1024) {
      setDropError('File size exceeds the 10 MB limit.');
      return;
    }
    navigate('/datasets/upload', { state: { file: selectedFile } });
  }, [navigate]);

  useEffect(() => {
    let dragCounter = 0;

    const handleDragEnter = (e: DragEvent) => {
      e.preventDefault();
      dragCounter++;
      if (e.dataTransfer && e.dataTransfer.types.includes('Files')) {
        setIsWindowDragging(true);
      }
    };

    const handleDragLeave = (e: DragEvent) => {
      e.preventDefault();
      dragCounter--;
      if (dragCounter <= 0) {
        setIsWindowDragging(false);
        dragCounter = 0;
      }
    };

    const handleDragOver = (e: DragEvent) => {
      e.preventDefault();
    };

    const handleDrop = (e: DragEvent) => {
      e.preventDefault();
      dragCounter = 0;
      setIsWindowDragging(false);
      const dropped = e.dataTransfer?.files?.[0];
      if (dropped) {
        handleFileSelected(dropped);
      }
    };

    window.addEventListener('dragenter', handleDragEnter);
    window.addEventListener('dragleave', handleDragLeave);
    window.addEventListener('dragover', handleDragOver);
    window.addEventListener('drop', handleDrop);

    return () => {
      window.removeEventListener('dragenter', handleDragEnter);
      window.removeEventListener('dragleave', handleDragLeave);
      window.removeEventListener('dragover', handleDragOver);
      window.removeEventListener('drop', handleDrop);
    };
  }, [handleFileSelected]);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['datasets', page],
    queryFn: () => listDatasets({ page, size: 20, sort: 'createdAt', direction: 'desc' }),
  });

  const renameMutation = useMutation({
    mutationFn: () => {
      if (!editingDataset) throw new Error('No dataset selected');
      return renameDataset(editingDataset.id, renameInput.trim());
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
      setEditingDataset(null);
      setRenameInput('');
      setDialogError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setDialogError(err.detail || `Rename failed: ${err.code}`);
      } else {
        setDialogError('Failed to rename dataset. Please try again.');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => {
      if (!deletingDataset) throw new Error('No dataset selected');
      return deleteDataset(deletingDataset.id, confirmDeleteInput.trim());
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['datasets'] });
      setDeletingDataset(null);
      setConfirmDeleteInput('');
      setDialogError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setDialogError(err.detail || `Delete failed: ${err.code}`);
      } else {
        setDialogError('Failed to delete dataset. Please try again.');
      }
    },
  });

  const datasets = data?.content ?? [];

  return (
    <div className="min-h-screen bg-[#f6f5f4] dark:bg-[#09090b] text-black dark:text-white relative transition-colors">
      {/* Fullscreen Drag Overlay */}
      {isWindowDragging && (
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            setIsWindowDragging(false);
            const dropped = e.dataTransfer?.files?.[0];
            if (dropped) handleFileSelected(dropped);
          }}
          className="fixed inset-0 z-50 bg-[#0075de]/20 backdrop-blur-xs border-4 border-dashed border-[#0075de] flex flex-col items-center justify-center p-6 animate-in fade-in duration-150 pointer-events-auto"
        >
          <div className="bg-white rounded-2xl shadow-2xl p-10 flex flex-col items-center text-center max-w-md w-full border border-black/10">
            <div className="w-16 h-16 rounded-2xl bg-[#0075de]/10 text-[#0075de] flex items-center justify-center mb-4">
              <Upload className="w-8 h-8 animate-bounce" />
            </div>
            <h2 className="text-xl font-bold text-black mb-1">Drop spreadsheet to upload</h2>
            <p className="text-xs text-[#666]">
              Release your .CSV or .XLSX file anywhere to validate and review your dataset.
            </p>
          </div>
        </div>
      )}

      {/* Top Navigation */}
      <header className="border-b border-black/10 dark:border-white/10 bg-white dark:bg-[#0f0f10] sticky top-0 z-30 shadow-sm transition-colors">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Logo />
            <span className="text-sm font-semibold text-[#555] dark:text-[#a1a1aa] border-l-2 border-black/10 dark:border-white/10 pl-4 py-0.5">
              Datasets
            </span>
          </div>

          <div className="flex items-center gap-3">
            <ThemeToggle />
            {user && (
              <div className="flex items-center gap-3 border-l border-black/10 dark:border-white/10 pl-3">
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
      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">Sales Datasets</h1>
            <p className="text-sm text-[#757575] dark:text-[#a1a1aa] mt-1">
              Manage uploaded sales files, review data quality, and explore KPI analytics.
            </p>
          </div>
          <Link
            to="/datasets/upload"
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-lg bg-black text-white hover:bg-black/85 dark:bg-white dark:text-black dark:hover:bg-white/90 transition-colors shadow-xs"
          >
            <span className="text-sm leading-none">+</span>
            <span>Upload Dataset</span>
          </Link>
        </div>

        {/* Dataset Table / List */}
        <div className="bg-white dark:bg-[#0f0f10] border border-black/8 dark:border-white/10 rounded-xl shadow-xs overflow-hidden">
          {isLoading ? (
            <div className="py-20 text-center text-xs text-[#757575] dark:text-[#a1a1aa]">Loading datasets...</div>
          ) : isError ? (
            <div className="py-20 text-center text-xs text-red-600 dark:text-red-400">
              Error loading datasets: {(error as Error)?.message}
            </div>
          ) : datasets.length === 0 ? (
            <div className="p-8 sm:p-12">
              <div
                onDragOver={(e) => {
                  e.preventDefault();
                  setIsDragging(true);
                }}
                onDragLeave={(e) => {
                  e.preventDefault();
                  if (e.currentTarget.contains(e.relatedTarget as Node)) return;
                  setIsDragging(false);
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsDragging(false);
                  const dropped = e.dataTransfer.files?.[0];
                  if (dropped) handleFileSelected(dropped);
                }}
                onClick={() => fileInputRef.current?.click()}
                className={`relative border-2 border-dashed rounded-2xl p-10 sm:p-14 text-center cursor-pointer transition-all duration-200 group ${
                  isDragging
                    ? 'border-[#0075de] bg-[#0075de]/10 dark:bg-[#0075de]/20 scale-[1.005] ring-4 ring-[#0075de]/20'
                    : 'border-black/20 dark:border-white/15 bg-[#faf9f8] dark:bg-[#141416] hover:border-black/40 dark:hover:border-white/30 hover:bg-[#f5f4f2] dark:hover:bg-[#1c1c1f]'
                }`}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv,.xlsx"
                  className="hidden"
                  onChange={(e) => {
                    const selected = e.target.files?.[0];
                    if (selected) handleFileSelected(selected);
                  }}
                />

                <div
                  className={`w-16 h-16 rounded-2xl mx-auto mb-4 flex items-center justify-center transition-all ${
                    isDragging
                      ? 'bg-[#0075de] text-white scale-110 shadow-md'
                      : 'bg-black/5 dark:bg-white/10 text-[#333] dark:text-[#f4f4f5] group-hover:bg-[#0075de]/10 group-hover:text-[#0075de] group-hover:scale-105'
                  }`}
                >
                  <Upload className="w-8 h-8" />
                </div>

                <h3 className="text-base font-bold text-black dark:text-white mb-1">
                  {isDragging ? 'Drop your spreadsheet here to upload' : 'Drag & drop your sales spreadsheet here'}
                </h3>
                <p className="text-xs text-[#666] dark:text-[#a1a1aa] max-w-sm mx-auto mb-5 leading-relaxed">
                  Supports <span className="font-semibold text-black dark:text-white">CSV</span> or <span className="font-semibold text-black dark:text-white">Excel (.XLSX)</span> files up to 10 MB.
                </p>

                <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      fileInputRef.current?.click();
                    }}
                    className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold rounded-lg bg-black text-white hover:bg-[#0075de] dark:bg-white dark:text-black dark:hover:bg-[#0075de] dark:hover:text-white transition-colors shadow-sm cursor-pointer"
                  >
                    <FileSpreadsheet className="w-4 h-4" />
                    <span>Browse Files</span>
                  </button>
                  <Link
                    to="/datasets/upload"
                    onClick={(e) => e.stopPropagation()}
                    className="text-xs text-[#666] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white font-medium underline underline-offset-4"
                  >
                    Or open manual upload form →
                  </Link>
                </div>

                {dropError && (
                  <div
                    onClick={(e) => e.stopPropagation()}
                    className="mt-5 p-3 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 text-red-700 dark:text-red-400 text-xs flex items-center justify-center gap-2 max-w-md mx-auto"
                  >
                    <AlertCircle className="w-4 h-4 shrink-0 text-red-600 dark:text-red-400" />
                    <span>{dropError}</span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[#fcfbf9] dark:bg-[#141416] border-b border-black/8 dark:border-white/10 text-[#757575] dark:text-[#a1a1aa] uppercase text-[10px] font-semibold tracking-wider">
                  <tr>
                    <th className="py-3 px-4">Dataset Name</th>
                    <th className="py-3 px-4 w-28">Status</th>
                    <th className="py-3 px-4 text-right">Rows</th>
                    <th className="py-3 px-4">Date Range</th>
                    <th className="py-3 px-4">Created</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-black/5 dark:divide-white/5">
                  {datasets.map((ds) => (
                    <tr key={ds.id} className="hover:bg-[#f9f8f6] dark:hover:bg-[#18181b] transition-colors">
                      <td className="py-3.5 px-4 font-medium text-black dark:text-white">
                        <Link
                          to={ds.status === 'ACTIVE' ? `/datasets/${ds.id}/analytics` : `/datasets/${ds.id}/preview`}
                          className="hover:text-[#0075de] hover:underline"
                        >
                          {ds.name}
                        </Link>
                      </td>
                      <td className="py-3.5 px-4">
                        {ds.status === 'ACTIVE' ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                            ACTIVE
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-800">
                            DRAFT
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono text-[#37352f] dark:text-[#e4e4e7]">
                        {ds.rowCount.toLocaleString()}
                      </td>
                      <td className="py-3.5 px-4 text-[#757575] dark:text-[#a1a1aa]">
                        {ds.minOrderDate && ds.maxOrderDate
                          ? `${ds.minOrderDate} to ${ds.maxOrderDate}`
                          : '—'}
                      </td>
                      <td className="py-3.5 px-4 text-[#757575] dark:text-[#a1a1aa]">
                        {new Date(ds.createdAt).toLocaleDateString()}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {ds.status === 'ACTIVE' && (
                            <Link
                              to={`/datasets/${ds.id}/analytics`}
                              className="px-2.5 py-1 text-xs font-medium text-[#0075de] hover:bg-[#0075de]/10 rounded transition-colors"
                            >
                              Analytics
                            </Link>
                          )}
                          <Link
                            to={`/datasets/${ds.id}/preview`}
                            className="px-2.5 py-1 text-xs font-medium text-[#37352f] dark:text-[#e4e4e7] hover:bg-black/5 dark:hover:bg-white/10 rounded transition-colors"
                          >
                            Preview
                          </Link>
                          <button
                            onClick={() => {
                              setEditingDataset(ds);
                              setRenameInput(ds.name);
                              setDialogError(null);
                            }}
                            className="px-2 py-1 text-xs text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 rounded transition-colors"
                          >
                            Rename
                          </button>
                          <button
                            onClick={() => {
                              setDeletingDataset(ds);
                              setConfirmDeleteInput('');
                              setDialogError(null);
                            }}
                            className="px-2 py-1 text-xs text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-950/30 rounded transition-colors"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="border-t border-black/8 dark:border-white/10 px-6 py-3 bg-[#fcfbf9] dark:bg-[#141416] flex items-center justify-between text-xs text-[#757575] dark:text-[#a1a1aa]">
              <span>
                Page {page + 1} of {data.totalPages} ({data.totalElements} datasets)
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={page === 0}
                  className="px-3 py-1 bg-white dark:bg-[#18181b] border border-black/10 dark:border-white/15 rounded hover:bg-black/5 dark:hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed font-medium text-black dark:text-white"
                >
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(p + 1, data.totalPages - 1))}
                  disabled={page >= data.totalPages - 1}
                  className="px-3 py-1 bg-white dark:bg-[#18181b] border border-black/10 dark:border-white/15 rounded hover:bg-black/5 dark:hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed font-medium text-black dark:text-white"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      </main>

      {/* Rename Modal */}
      {editingDataset && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white dark:bg-[#141416] rounded-xl shadow-xl max-w-md w-full p-6 border border-black/10 dark:border-white/15 text-black dark:text-white">
            <h3 className="text-base font-semibold text-black dark:text-white mb-1">Rename Dataset</h3>
            <p className="text-xs text-[#757575] dark:text-[#a1a1aa] mb-4">
              Enter a new name for this dataset (unique across your account).
            </p>

            {dialogError && (
              <div className="mb-4 p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 text-xs text-red-700 dark:text-red-400 rounded-lg">
                {dialogError}
              </div>
            )}

            <form
              onSubmit={(e) => {
                e.preventDefault();
                renameMutation.mutate();
              }}
            >
              <input
                type="text"
                required
                maxLength={100}
                value={renameInput}
                onChange={(e) => setRenameInput(e.target.value)}
                className="w-full px-3 py-2 text-sm bg-white dark:bg-[#1c1c1f] border border-black/15 dark:border-white/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] mb-4 text-black dark:text-white"
              />

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setEditingDataset(null)}
                  className="px-3 py-1.5 text-xs text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white bg-white dark:bg-[#18181b] hover:bg-black/5 dark:hover:bg-white/10 border border-black/10 dark:border-white/15 rounded-lg cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!renameInput.trim() || renameMutation.isPending}
                  className="px-4 py-1.5 text-xs font-semibold text-white bg-[#0075de] hover:bg-[#0060b8] disabled:opacity-50 rounded-lg cursor-pointer"
                >
                  {renameMutation.isPending ? 'Saving...' : 'Save Name'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deletingDataset && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white dark:bg-[#141416] rounded-xl shadow-xl max-w-md w-full p-6 border border-black/10 dark:border-white/15 text-black dark:text-white">
            <h3 className="text-base font-semibold text-red-700 dark:text-red-400 mb-1">Delete Dataset</h3>
            <p className="text-xs text-[#757575] dark:text-[#a1a1aa] mb-3">
              This action cannot be undone. All sales records, staging data, and import history will be permanently deleted.
            </p>
            <p className="text-xs text-[#37352f] dark:text-[#e4e4e7] mb-2 font-medium">
              Please type <strong className="text-black dark:text-white select-all bg-black/5 dark:bg-white/10 px-1 py-0.5 rounded font-mono">{deletingDataset.name}</strong> to confirm:
            </p>

            {dialogError && (
              <div className="mb-4 p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 text-xs text-red-700 dark:text-red-400 rounded-lg">
                {dialogError}
              </div>
            )}

            <form
              onSubmit={(e) => {
                e.preventDefault();
                deleteMutation.mutate();
              }}
            >
              <input
                type="text"
                required
                value={confirmDeleteInput}
                onChange={(e) => setConfirmDeleteInput(e.target.value)}
                placeholder={deletingDataset.name}
                className="w-full px-3 py-2 text-sm bg-white dark:bg-[#1c1c1f] border border-black/15 dark:border-white/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-red-500/30 focus:border-red-600 mb-4 font-mono text-black dark:text-white"
              />

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setDeletingDataset(null)}
                  className="px-3 py-1.5 text-xs text-[#757575] dark:text-[#a1a1aa] hover:text-black dark:hover:text-white bg-white dark:bg-[#18181b] hover:bg-black/5 dark:hover:bg-white/10 border border-black/10 dark:border-white/15 rounded-lg cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={
                    confirmDeleteInput.trim() !== deletingDataset.name || deleteMutation.isPending
                  }
                  className="px-4 py-1.5 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg shadow-xs cursor-pointer"
                >
                  {deleteMutation.isPending ? 'Deleting...' : 'Permanently Delete'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
