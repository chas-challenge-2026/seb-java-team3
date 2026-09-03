import {
  createRouter,
  createRootRoute,
  createRoute,
  Outlet,
} from "@tanstack/react-router";
import { Dashboard } from "./pages/Dashboard";
import { Login } from "./pages/Login";
import { UITestPage } from "./pages/UIComponentTests";
import { NewPayment } from "./pages/NewPayment"

const rootRoute = createRootRoute({
  component: () => <Outlet />,
});

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  component: Login,
});

const uiTestRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/uitest",
  component: UITestPage,
});

const newPaymentRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/payments/new",
  component: NewPayment,
});

const authRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "auth",
  component: () => <Outlet />,
});

const dashboardRoute = createRoute({
  getParentRoute: () => authRoute,
  path: "/",
  component: Dashboard,
});

const routeTree = rootRoute.addChildren([
  loginRoute,
  uiTestRoute,
  newPaymentRoute,
  authRoute.addChildren([dashboardRoute]),
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
    interface Register { router: typeof router; }
}
