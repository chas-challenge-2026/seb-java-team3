import {
  createRouter,
  createRootRouteWithContext,
  createRoute,
  Outlet,
} from "@tanstack/react-router";

import { Dashboard } from "./pages/Dashboard";
import { Login } from "./pages/Login";
import { UITestPage } from "./pages/UIComponentTests";
import { NewPayment } from "./pages/NewPayment";

import type { QueryClient } from "@tanstack/react-query";
import { queryClient } from "./lib/queryClient";
import { requireAuth } from "./lib/requireAuth";
import AppLayout from "./components/ui/layout/AppLayout";

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
  component: NewPayment,
});

const dashboardRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/",
  component: Dashboard,
});

const routeTree = rootRoute.addChildren([
  loginRoute,

  authRoute.addChildren([
    dashboardRoute,
    uiTestRoute,
    newPaymentRoute,
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