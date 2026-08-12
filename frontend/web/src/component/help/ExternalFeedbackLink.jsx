const defaultFeedbackUrl = "https://wj.qq.com/s2/27565116/i1wq/";
const configuredFeedbackUrl = (import.meta.env?.VITE_FEEDBACK_URL || defaultFeedbackUrl).trim();

function validExternalUrl(value) {
  if (!value) return "";
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.toString() : "";
  } catch {
    return "";
  }
}

export const feedbackUrl = validExternalUrl(configuredFeedbackUrl);

export function ExternalFeedbackLink({ children, className = "", ...props }) {
  const configured = Boolean(feedbackUrl);
  const content = typeof children === "function" ? children(configured) : children;
  const classes = ["external-feedback-link", className, !configured && "is-disabled"]
    .filter(Boolean)
    .join(" ");

  if (!configured) {
    return (
      <span
        {...props}
        className={classes}
        aria-disabled="true"
        title="反馈问卷链接尚未配置"
      >
        {content}
      </span>
    );
  }

  return (
    <a
      {...props}
      className={classes}
      href={feedbackUrl}
      target="_blank"
      rel="noreferrer"
    >
      {content}
    </a>
  );
}
