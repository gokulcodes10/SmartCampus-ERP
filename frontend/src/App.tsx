import AppRouter from "@/routes/AppRouter";

/**
 * Phase 2. The route tree (auth pages, protected/role-gated dashboards) lives in
 * AppRouter, which owns its own BrowserRouter and AuthProvider — this component
 * exists only to satisfy Vite's entry point.
 */
function App() {
  return <AppRouter />;
}

export default App;
