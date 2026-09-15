import {
  createRouter,
  createRootRouteWithContext,
  createRoute,
  Outlet,
} from "@tanstack/react-router";
import AppLayout from "./components/ui/layout/AppLayout";

import { Dashboard } from "./pages/Dashboard";
import { Login } from "./pages/Login";
import { UITestPage } from "./pages/UIComponentTests";
import { NewPayment } from "./features/payment/NewPayment"
import AuditPage from "./features/audit/pages/AuditPage"
import PaymentAuditTimelinePage from "./features/audit/pages/PaymentAuditTimelinePage";
import AttestPage from "./pages/AttestPage";

import type { QueryClient } from "@tanstack/react-query";
import { queryClient } from "./lib/queryClient";
import { requireAuth } from "./lib/requireAuth";
import { requireRole } from "./lib/requireRole";

interface RouterContext {
  queryClient: QueryClient;
}

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: () => <Outlet />,
});

// Login
const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  component: Login,
});

// Alla routes under denna kräver auth
const authRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "auth",
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: AppLayout,
});

const uiTestRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/uitest",
  component: UITestPage,
});

const newPaymentRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/payments/new",
  beforeLoad: ({ context }) => requireRole(context.queryClient, ["INITIATOR", "ADMIN"]),
  component: NewPayment,
});

const dashboardRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/",
  component: Dashboard,
});

const auditRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/audit",
  component: AuditPage,
});

const attestRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/attest",
  component: AttestPage,
})

const paymentAuditRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/payments/$paymentId/audit",
  component: PaymentAuditTimelinePage,
});

const routeTree = rootRoute.addChildren([
  loginRoute,

  authRoute.addChildren([
    dashboardRoute,
    uiTestRoute,
    newPaymentRoute,
    auditRoute,
    attestRoute,
    paymentAuditRoute,
  ]),
]);

export const router = createRouter({
  routeTree,
  context: { queryClient },
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
