import React from "react";
import { createRoot } from "react-dom/client";
import { AchievementNotificationProvider } from "./AchievementNotifications.jsx";
import { App } from "./App.jsx";
import "./styles.css";

class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error("UniSpeaking render failed", error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <main className="app-fatal-error" role="alert">
        <h1>页面加载失败</h1>
        <p>{this.state.error.message || "前端发生未知错误"}</p>
        <button type="button" onClick={() => window.location.reload()}>重新加载</button>
      </main>
    );
  }
}

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <AchievementNotificationProvider>
        <App />
      </AchievementNotificationProvider>
    </AppErrorBoundary>
  </React.StrictMode>,
);
