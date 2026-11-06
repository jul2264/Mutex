import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { PreviewPage } from './pages/PreviewPage';
import { DatasetListPage } from './pages/DatasetListPage';
import { UploadPage } from './pages/UploadPage';
import { DatasetDetailPage } from './pages/DatasetDetailPage';
import { ReportsPage } from './pages/ReportsPage';
import { RequireAuth } from './components/auth/RequireAuth';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <DatasetListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/datasets"
            element={
              <RequireAuth>
                <DatasetListPage />
              </RequireAuth>
            }
          />
          <Route
            path="/datasets/upload"
            element={
              <RequireAuth>
                <UploadPage />
              </RequireAuth>
            }
          />
          <Route
            path="/datasets/:datasetId"
            element={
              <RequireAuth>
                <DatasetDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="/datasets/:datasetId/preview"
            element={
              <RequireAuth>
                <PreviewPage />
              </RequireAuth>
            }
          />
          <Route
            path="/datasets/:datasetId/analytics"
            element={
              <RequireAuth>
                <AnalyticsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/datasets/:datasetId/reports"
            element={
              <RequireAuth>
                <ReportsPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </ThemeProvider>
  );
}
