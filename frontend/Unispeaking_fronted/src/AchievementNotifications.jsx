import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Check, Trophy, X } from "@phosphor-icons/react";
import {
  acknowledgeAchievementUnlock,
  syncAchievementUnlocks,
} from "./apiClient.js";

const AUTO_DISMISS_MS = 5_000;
const AchievementNotificationContext = createContext(null);

function AchievementPopup({ notification, remaining, onDismiss }) {
  const dismiss = useCallback(() => {
    if (notification) onDismiss(notification.achievementId);
  }, [notification, onDismiss]);

  useEffect(() => {
    if (!notification) return undefined;
    const timer = window.setTimeout(dismiss, AUTO_DISMISS_MS);
    return () => window.clearTimeout(timer);
  }, [dismiss, notification]);

  useEffect(() => {
    if (!notification) return undefined;
    const handleKeyDown = (event) => {
      if (event.key === "Escape") dismiss();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [dismiss, notification]);

  if (!notification) return null;

  return (
    <aside
      className="achievement-popup"
      role="status"
      aria-live="polite"
      aria-label={`成就已达成：${notification.title}`}
    >
      <button
        className="achievement-popup__close"
        type="button"
        aria-label="关闭成就通知"
        onClick={dismiss}
      >
        <X weight="bold" />
      </button>
      <div className="achievement-popup__badge" aria-hidden="true">
        <Trophy weight="fill" />
        <span><Check weight="bold" /></span>
      </div>
      <p className="achievement-popup__eyebrow">ACHIEVEMENT UNLOCKED</p>
      <h2>成就已达成</h2>
      <strong>{notification.title}</strong>
      <p className="achievement-popup__description">{notification.description}</p>
      <div className="achievement-popup__meta">
        <span>{notification.category}</span>
        <i aria-hidden="true" />
        <span>{notification.seriesTitle} · Lv.{notification.level}</span>
      </div>
      {remaining > 0 && (
        <small className="achievement-popup__remaining">还有 {remaining} 个成就待展示</small>
      )}
      <span className="achievement-popup__timer" aria-hidden="true" />
    </aside>
  );
}

export function AchievementNotificationProvider({ children }) {
  const [queue, setQueue] = useState([]);
  const seenIdsRef = useRef(new Set());
  const pendingAcknowledgementsRef = useRef(new Set());
  const inFlightRef = useRef(null);
  const rerunRequestedRef = useRef(false);
  const revealRequestedRef = useRef(false);
  const generationRef = useRef(0);

  const retryPendingAcknowledgements = useCallback(async (generation) => {
    const pendingIds = [...pendingAcknowledgementsRef.current];
    await Promise.all(pendingIds.map(async (achievementId) => {
      try {
        await acknowledgeAchievementUnlock(achievementId);
        if (generation === generationRef.current) {
          pendingAcknowledgementsRef.current.delete(achievementId);
        }
      } catch {
        // Keep the acknowledgement pending for the next safe synchronization.
      }
    }));
  }, []);

  const synchronizeAchievements = useCallback(({ revealNotifications = false } = {}) => {
    if (revealNotifications) revealRequestedRef.current = true;
    if (inFlightRef.current) {
      rerunRequestedRef.current = true;
      return inFlightRef.current;
    }

    const generation = generationRef.current;
    const synchronize = async () => {
      let latestResponse = null;
      do {
        const shouldRevealNotifications = revealRequestedRef.current;
        revealRequestedRef.current = false;
        rerunRequestedRef.current = false;
        try {
          latestResponse = await syncAchievementUnlocks();
          if (generation !== generationRef.current) return null;
          if (shouldRevealNotifications) {
            const pending = Array.isArray(latestResponse?.pendingNotifications)
              ? latestResponse.pendingNotifications
              : [];
            setQueue((current) => {
              const queuedIds = new Set(current.map((item) => item.achievementId));
              const additions = pending.filter((item) => {
                const achievementId = String(item?.achievementId || "").trim();
                if (!achievementId
                  || queuedIds.has(achievementId)
                  || seenIdsRef.current.has(achievementId)) {
                  return false;
                }
                queuedIds.add(achievementId);
                seenIdsRef.current.add(achievementId);
                return true;
              });
              return additions.length ? [...current, ...additions] : current;
            });
          }
          await retryPendingAcknowledgements(generation);
        } catch {
          // Achievement synchronization must never interrupt the completed workflow.
        }
      } while ((rerunRequestedRef.current || revealRequestedRef.current)
        && generation === generationRef.current);
      return latestResponse;
    };

    const request = synchronize().finally(() => {
      if (inFlightRef.current === request) inFlightRef.current = null;
    });
    inFlightRef.current = request;
    return request;
  }, [retryPendingAcknowledgements]);

  const dismissCurrentAchievement = useCallback((achievementId) => {
    const dismissedId = String(achievementId || "").trim();
    if (!dismissedId) return;
    setQueue((current) => current[0]?.achievementId === dismissedId
      ? current.slice(1)
      : current.filter((item) => item.achievementId !== dismissedId));

    const generation = generationRef.current;
    pendingAcknowledgementsRef.current.add(dismissedId);
    void acknowledgeAchievementUnlock(dismissedId)
      .then(() => {
        if (generation === generationRef.current) {
          pendingAcknowledgementsRef.current.delete(dismissedId);
        }
      })
      .catch(() => {
        // The next synchronization retries without showing the same popup twice.
      });
  }, []);

  const clearAchievementNotifications = useCallback(() => {
    generationRef.current += 1;
    rerunRequestedRef.current = false;
    revealRequestedRef.current = false;
    inFlightRef.current = null;
    seenIdsRef.current.clear();
    pendingAcknowledgementsRef.current.clear();
    setQueue([]);
  }, []);

  const contextValue = useMemo(() => ({
    synchronizeAchievements,
    clearAchievementNotifications,
  }), [clearAchievementNotifications, synchronizeAchievements]);

  return (
    <AchievementNotificationContext.Provider value={contextValue}>
      {children}
      <AchievementPopup
        notification={queue[0] || null}
        remaining={Math.max(0, queue.length - 1)}
        onDismiss={dismissCurrentAchievement}
      />
    </AchievementNotificationContext.Provider>
  );
}

export function useAchievementNotifications() {
  const context = useContext(AchievementNotificationContext);
  if (!context) {
    throw new Error("useAchievementNotifications must be used within AchievementNotificationProvider");
  }
  return context;
}
