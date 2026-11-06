import React, { useState, useCallback } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { uploadDatasetFile } from '../api/imports';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/apiFetch';
import { Upload, ArrowLeft, AlertCircle, FileSpreadsheet, CheckCircle2 } from 'lucide-react';

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

export const UploadPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, isLoggingOut } = useAuth();

  const stateFile = (location.state as { file?: File } | null)?.file;
  const initialFile = stateFile instanceof File ? stateFile : null;
  const initialName = initialFile
    ? (() => {
        const base = initialFile.name.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
        return base.charAt(0).toUpperCase() + base.slice(1);
      })()
    : '';

  const [name, setName] = useState<string>(initialName);
  const [file, setFile] = useState<File | null>(initialFile);
  const [dateFormat, setDateFormat] = useState<'ISO' | 'DMY' | 'MDY'>('ISO');
  const [policy, setPolicy] = useState<'IMPORT_VALID_ONLY' | 'REJECT_FILE_ON_ANY_ERROR'>('IMPORT_VALID_ONLY');
  const [currency, setCurrency] = useState<string>('INR');

  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState<boolean>(false);

  const handleFileChange = useCallback((selectedFile: File | null) => {
    setErrorMessage(null);
    if (!selectedFile) {
      setFile(null);
      return;
    }

    const lower = selectedFile.name.toLowerCase();
    if (!lower.endsWith('.csv') && !lower.endsWith('.xlsx')) {
      setErrorMessage('Invalid file format. Please upload a .csv or .xlsx sales spreadsheet.');
      setFile(null);
      return;
    }

    if (selectedFile.size > MAX_FILE_SIZE_BYTES) {
      setErrorMessage('File size exceeds 10 MB limit. Please upload a smaller file or split the records.');
      setFile(null);
      return;
    }

    setFile(selectedFile);
    setName((prev) => {
      if (!prev.trim()) {
        const baseName = selectedFile.name.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
        return baseName.charAt(0).toUpperCase() + baseName.slice(1);
      }
      return prev;
    });
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const trimmedName = name.trim();
    if (!trimmedName || trimmedName.length > 100) {
      setErrorMessage('Dataset name is required and must be between 1 and 100 characters.');
      return;
    }

    if (!file) {
      setErrorMessage('Please select a CSV or XLSX file to upload.');
      return;
    }

    setIsUploading(true);
    setUploadProgress(0);

    try {
      const response = await uploadDatasetFile({
        name: trimmedName,
        file,
        dateFormat,
        policy,
        currency,
        onProgress: (percent) => setUploadProgress(percent),
      });

      // Redirect directly to the review preview page
      navigate(`/datasets/${response.datasetId}/preview?jobId=${response.importJobId}`);
    } catch (err: unknown) {
      setIsUploading(false);
      setUploadProgress(null);
      if (err instanceof ApiError) {
        setErrorMessage(err.detail || err.message || `Upload failed with code: ${err.code}`);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('An unexpected error occurred during upload. Please try again.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-[#f6f5f4] text-black">
      {/* Top Header */}
      <header className="border-b border-black/10 bg-white sticky top-0 z-30 shadow-sm">
        <div className="max-w-5xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link to="/" className="font-bold text-2xl tracking-tight text-black flex items-center gap-3 hover:opacity-85 transition-opacity">
              <span className="w-10 h-10 rounded-xl bg-[#0075de] text-white text-base font-extrabold flex items-center justify-center shadow-sm">
                μ
              </span>
              Mutex
            </Link>
            <span className="text-sm font-semibold text-[#555] border-l-2 border-black/10 pl-4 py-0.5">
              Upload Dataset
            </span>
          </div>

          <div className="flex items-center gap-4">
            {user && (
              <div className="flex items-center gap-3">
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

      {/* Main Container */}
      <main className="max-w-4xl mx-auto px-6 py-8">
        <div className="mb-6">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-xs text-[#757575] hover:text-black transition-colors mb-3"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Datasets
          </Link>
          <h1 className="text-2xl font-bold tracking-tight text-black">Upload Sales Dataset</h1>
          <p className="text-sm text-[#757575] mt-1">
            Ingest a sales spreadsheet (CSV or XLSX). Mutex validates every row, highlights issues, and lets you review before confirming.
          </p>
        </div>

        {errorMessage && (
          <div className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm flex items-start gap-3">
            <AlertCircle className="w-5 h-5 shrink-0 text-red-600 mt-0.5" />
            <div className="flex-1">
              <strong className="font-semibold">Upload failed:</strong> {errorMessage}
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-black/8 shadow-xs p-6 space-y-6">
          {/* Dataset Name */}
          <div>
            <label htmlFor="dataset-name" className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-1.5">
              Dataset Name <span className="text-red-500">*</span>
            </label>
            <input
              id="dataset-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Q1 2026 Enterprise Sales"
              disabled={isUploading}
              maxLength={100}
              required
              className="w-full px-3.5 py-2.5 rounded-lg border border-black/15 text-sm focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] transition-all bg-[#faf9f8]"
            />
            <p className="text-xs text-[#757575] mt-1">Between 1 and 100 characters. Unique to your account.</p>
          </div>

          {/* File Upload Drop Area */}
          <div>
            <label className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-1.5">
              Spreadsheet File (.CSV or .XLSX) <span className="text-red-500">*</span>
            </label>
            <div
              onDragOver={(e) => {
                e.preventDefault();
                if (!isUploading) setIsDragOver(true);
              }}
              onDragLeave={(e) => {
                e.preventDefault();
                if (e.currentTarget.contains(e.relatedTarget as Node)) return;
                setIsDragOver(false);
              }}
              onDrop={(e) => {
                e.preventDefault();
                setIsDragOver(false);
                if (isUploading) return;
                const dropped = e.dataTransfer.files?.[0];
                if (dropped) handleFileChange(dropped);
              }}
              className={`border-2 border-dashed rounded-xl p-8 text-center transition-all ${
                isDragOver
                  ? 'border-[#0075de] bg-[#0075de]/10 ring-4 ring-[#0075de]/20'
                  : file
                  ? 'border-emerald-400 bg-emerald-50/30'
                  : 'border-black/15 bg-[#faf9f8] hover:border-black/30'
              }`}
            >
              <input
                id="file-input"
                type="file"
                accept=".csv,.xlsx"
                disabled={isUploading}
                onChange={(e) => handleFileChange(e.target.files?.[0] || null)}
                className="hidden"
              />
              <label htmlFor="file-input" className="cursor-pointer flex flex-col items-center">
                {file ? (
                  <>
                    <CheckCircle2 className="w-10 h-10 text-emerald-600 mb-2" />
                    <span className="text-sm font-semibold text-black">{file.name}</span>
                    <span className="text-xs text-[#757575] mt-1">
                      {(file.size / 1024).toFixed(1)} KB · Click or drag to change file
                    </span>
                  </>
                ) : (
                  <>
                    <div className="w-12 h-12 rounded-full bg-[#0075de]/10 flex items-center justify-center text-[#0075de] mb-3">
                      <FileSpreadsheet className="w-6 h-6" />
                    </div>
                    <span className="text-sm font-semibold text-black">
                      {isDragOver ? 'Drop file to upload' : 'Click to browse or drag and drop'}
                    </span>
                    <span className="text-xs text-[#757575] mt-1">Supports UTF-8 CSV or Excel XLSX (max 10 MB)</span>
                  </>
                )}
              </label>
            </div>
          </div>

          {/* Configuration Options Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5 pt-2 border-t border-black/8">
            {/* Date Format */}
            <div>
              <label htmlFor="date-format" className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-1.5">
                Date Format in File
              </label>
              <select
                id="date-format"
                value={dateFormat}
                disabled={isUploading}
                onChange={(e) => setDateFormat(e.target.value as 'ISO' | 'DMY' | 'MDY')}
                className="w-full px-3 py-2 rounded-lg border border-black/15 text-sm focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] bg-[#faf9f8]"
              >
                <option value="ISO">ISO (YYYY-MM-DD)</option>
                <option value="DMY">DMY (DD/MM/YYYY)</option>
                <option value="MDY">MDY (MM/DD/YYYY)</option>
              </select>
            </div>

            {/* Currency Code */}
            <div>
              <label htmlFor="currency-code" className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-1.5">
                Currency
              </label>
              <input
                id="currency-code"
                type="text"
                value={currency}
                disabled={isUploading}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                maxLength={3}
                placeholder="INR"
                className="w-full px-3 py-2 rounded-lg border border-black/15 text-sm focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] bg-[#faf9f8]"
              />
              <p className="text-xs text-[#757575] mt-1">ISO 4217 code (e.g. INR, USD)</p>
            </div>

            {/* Ingestion Policy */}
            <div>
              <label htmlFor="policy-select" className="block text-xs font-semibold text-[#37352f] uppercase tracking-wider mb-1.5">
                Import Policy
              </label>
              <select
                id="policy-select"
                value={policy}
                disabled={isUploading}
                onChange={(e) => setPolicy(e.target.value as 'IMPORT_VALID_ONLY' | 'REJECT_FILE_ON_ANY_ERROR')}
                className="w-full px-3 py-2 rounded-lg border border-black/15 text-sm focus:outline-none focus:ring-2 focus:ring-[#0075de]/30 focus:border-[#0075de] bg-[#faf9f8]"
              >
                <option value="IMPORT_VALID_ONLY">Import Valid Rows Only</option>
                <option value="REJECT_FILE_ON_ANY_ERROR">Strict (Reject on Any Error)</option>
              </select>
            </div>
          </div>

          {/* Upload Progress Bar */}
          {uploadProgress !== null && (
            <div className="pt-2">
              <div className="flex items-center justify-between text-xs font-medium text-[#37352f] mb-1.5">
                <span>{uploadProgress < 100 ? 'Uploading file...' : 'File received, starting server validation...'}</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="w-full h-2 rounded-full bg-black/10 overflow-hidden">
                <div
                  className="h-full bg-[#0075de] transition-all duration-300 rounded-full"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          {/* Submit Action */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-black/8">
            <Link
              to="/"
              className="px-4 py-2 rounded-lg text-sm text-[#757575] hover:text-black hover:bg-black/5 transition-colors font-medium"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={isUploading || !file || !name.trim()}
              className="px-5 py-2 rounded-lg bg-[#0075de] hover:bg-[#0060b8] text-white text-sm font-semibold shadow-xs disabled:opacity-50 disabled:pointer-events-none transition-all flex items-center gap-2 cursor-pointer"
            >
              <Upload className="w-4 h-4" />
              {isUploading ? 'Uploading...' : 'Upload & Review'}
            </button>
          </div>
        </form>
      </main>
    </div>
  );
};

