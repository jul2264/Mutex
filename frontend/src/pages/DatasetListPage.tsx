import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listDatasets, renameDataset, deleteDataset } from '../api/datasets';
import { useAuth } from '../hooks/useAuth';
import type { DatasetResponse } from '../api/types';
import { ApiError } from '../api/apiFetch';

export const DatasetListPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { user, logout, isLoggingOut } = useAuth();

  const [page, setPage] = useState<number>(0);
  const [editingDataset, setEditingDataset] = useState<DatasetResponse | null>(null);
  const [renameInput, setRenameInput] = useState<string>('');
  const [deletingDataset, setDeletingDataset] = useState<DatasetResponse | null>(null);
  const [confirmDeleteInput, setConfirmDeleteInput] = useState<string>('');
  const [dialogError, setDialogError] = useState<string | null>(null);

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
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Top Navigation */}
      <header className="border-b border-black/8 bg-[#f6f5f4] sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="font-bold text-xl tracking-tight text-black flex items-center gap-2">
              <span className="w-6 h-6 rounded bg-[#0075de] text-white text-xs font-bold flex items-center justify-center">
                μ
              </span>
              Mutex
            </span>
            <span className="text-xs text-[#757575] border-l border-black/10 pl-3">
              Datasets
            </span>
          </div>

          <div className="flex items-center gap-3">
            {user && (
              <div className="flex items-center gap-2">
                <span className="text-xs text-[#37352f] font-medium hidden sm:inline">
                  {user.displayName}
                </span>
                <button
                  onClick={() => logout()}
                  disabled={isLoggingOut}
                  className="text-xs text-[#757575] hover:text-black hover:bg-black/5 px-2 py-1 rounded transition-colors"
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
            <h1 className="text-2xl font-bold tracking-tight text-black">Sales Datasets</h1>
            <p className="text-sm text-[#757575] mt-1">
              Manage uploaded sales files, review data quality, and explore KPI analytics.
            </p>
          </div>
          <Link
            to="/datasets/upload"
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-lg bg-black text-white hover:bg-black/85 transition-colors shadow-xs"
          >
            <span className="text-sm leading-none">+</span>
            <span>Upload Dataset</span>
          </Link>
        </div>

        {/* Dataset Table / List */}
        <div className="bg-white border border-black/8 rounded-xl shadow-xs overflow-hidden">
          {isLoading ? (
            <div className="py-20 text-center text-xs text-[#757575]">Loading datasets...</div>
          ) : isError ? (
            <div className="py-20 text-center text-xs text-red-600">
              Error loading datasets: {(error as Error)?.message}
            </div>
          ) : datasets.length === 0 ? (
            <div className="py-20 text-center">
              <div className="w-12 h-12 rounded-full bg-black/5 text-[#757575] flex items-center justify-center mx-auto mb-3 text-lg font-mono">
                📁
              </div>
              <h3 className="text-sm font-semibold text-[#37352f]">No datasets yet</h3>
              <p className="text-xs text-[#757575] mt-1 max-w-sm mx-auto mb-4">
                Upload a CSV or XLSX spreadsheet to start exploring your business metrics.
              </p>
              <Link
                to="/datasets/upload"
                className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-lg bg-black text-white hover:bg-black/85 transition-colors shadow-xs"
              >
                <span className="text-sm leading-none">+</span>
                <span>Upload Dataset</span>
              </Link>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[#fcfbf9] border-b border-black/8 text-[#757575] uppercase text-[10px] font-semibold tracking-wider">
                  <tr>
                    <th className="py-3 px-4">Dataset Name</th>
                    <th className="py-3 px-4 w-28">Status</th>
                    <th className="py-3 px-4 text-right">Rows</th>
                    <th className="py-3 px-4">Date Range</th>
                    <th className="py-3 px-4">Created</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-black/5">
                  {datasets.map((ds) => (
                    <tr key={ds.id} className="hover:bg-[#f9f8f6] transition-colors">
                      <td className="py-3.5 px-4 font-medium text-black">
                        <Link
                          to={ds.status === 'ACTIVE' ? `/datasets/${ds.id}/analytics` : `/datasets/${ds.id}/preview`}
                          className="hover:text-[#0075de] hover:underline"
                        >
                          {ds.name}
                        </Link>
                      </td>
                      <td className="py-3.5 px-4">
                        {ds.status === 'ACTIVE' ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            ACTIVE
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                            DRAFT
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right font-mono text-[#37352f]">
                        {ds.rowCount.toLocaleString()}
                      </td>
                      <td className="py-3.5 px-4 text-[#757575]">
                        {ds.minOrderDate && ds.maxOrderDate
                          ? `${ds.minOrderDate} to ${ds.maxOrderDate}`
                          : '—'}
                      </td>
                      <td className="py-3.5 px-4 text-[#757575]">
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
                            className="px-2.5 py-1 text-xs font-medium text-[#37352f] hover:bg-black/5 rounded transition-colors"
                          >
                            Preview
                          </Link>
                          <button
                            onClick={() => {
                              setEditingDataset(ds);
                              setRenameInput(ds.name);
                              setDialogError(null);
                            }}
                            className="px-2 py-1 text-xs text-[#757575] hover:text-black hover:bg-black/5 rounded transition-colors"
                          >
                            Rename
                          </button>
                          <button
                            onClick={() => {
                              setDeletingDataset(ds);
                              setConfirmDeleteInput('');
                              setDialogError(null);
                            }}
                            className="px-2 py-1 text-xs text-red-600 hover:text-red-800 hover:bg-red-50 rounded transition-colors"
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
            <div className="border-t border-black/8 px-6 py-3 bg-[#fcfbf9] flex items-center justify-between text-xs text-[#757575]">
              <span>
                Page {page + 1} of {data.totalPages} ({data.totalElements} datasets)
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={page === 0}
                  className="px-3 py-1 bg-white border border-black/10 rounded hover:bg-black/5 disabled:opacity-40 disabled:cursor-not-allowed font-medium text-black"
                >
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(p + 1, data.totalPages - 1))}
                  disabled={page >= data.totalPages - 1}
                  className="px-3 py-1 bg-white border border-black/10 rounded hover:bg-black/5 disabled:opacity-40 disabled:cursor-not-allowed font-medium text-black"
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
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 border border-black/10">
            <h3 className="text-base font-semibold text-black mb-1">Rename Dataset</h3>
            <p className="text-xs text-[#757575] mb-4">
              Enter a new name for this dataset (unique across your account).
            </p>

            {dialogError && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 text-xs text-red-700 rounded-lg">
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
                className="w-full px-3 py-2 text-sm bg-white border border-black/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] mb-4"
              />

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setEditingDataset(null)}
                  className="px-3 py-1.5 text-xs text-[#757575] hover:text-black bg-white hover:bg-black/5 border border-black/10 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!renameInput.trim() || renameMutation.isPending}
                  className="px-4 py-1.5 text-xs font-semibold text-white bg-[#0075de] hover:bg-[#0060b8] disabled:opacity-50 rounded-lg"
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
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 border border-black/10">
            <h3 className="text-base font-semibold text-red-700 mb-1">Delete Dataset</h3>
            <p className="text-xs text-[#757575] mb-3">
              This action cannot be undone. All sales records, staging data, and import history will be permanently deleted.
            </p>
            <p className="text-xs text-[#37352f] mb-2 font-medium">
              Please type <strong className="text-black select-all bg-black/5 px-1 py-0.5 rounded font-mono">{deletingDataset.name}</strong> to confirm:
            </p>

            {dialogError && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 text-xs text-red-700 rounded-lg">
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
                className="w-full px-3 py-2 text-sm bg-white border border-black/15 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-red-500/30 focus:border-red-600 mb-4 font-mono"
              />

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setDeletingDataset(null)}
                  className="px-3 py-1.5 text-xs text-[#757575] hover:text-black bg-white hover:bg-black/5 border border-black/10 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={
                    confirmDeleteInput.trim() !== deletingDataset.name || deleteMutation.isPending
                  }
                  className="px-4 py-1.5 text-xs font-semibold text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg shadow-xs"
                >
                  {deleteMutation.isPending ? 'Deleting...' : 'Delete Dataset'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
