import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./lib/queryClient";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { RouterProvider } from "@tanstack/react-router";
import { router } from "./router";
import { applyTheme, getSavedTheme } from "./lib/theme";
import { onUserChanged } from "./lib/authSession";
import "./index.css";
import "./css/colors.css"
import "./css/appLayout.css";
applyTheme(getSavedTheme());

// Någon har loggat in som en annan användare i en annan flik (refresh-cookien delas mellan flikar).
// Ladda om, så att allt i den här fliken visar den användare som sessionen nu tillhör.
onUserChanged(() => window.location.assign("/"));


createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router}/>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </StrictMode>,
);
