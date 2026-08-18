# layouts/

Shell components that wrap pages with shared chrome — top nav, sidebar,
role-based navigation, footer. E.g. `AuthLayout` (login/register/forgot
password), `DashboardLayout` (authenticated app shell with sidebar +
notification bell, per §40's real-time notification centre), each wrapping
an `<Outlet />` from `react-router-dom`.

Layouts read auth/role state from `context/` to decide what navigation to
show; they do not fetch domain data themselves.
