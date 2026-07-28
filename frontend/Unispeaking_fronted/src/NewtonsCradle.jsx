export function NewtonsCradle({ size = 50, speed = "1.2s", color = "linear-gradient(135deg, #d8d8d2 0%, #a9a9a2 56%, #c7c7c0 100%)", label = "加载中", className = "" }) {
  return (
    <span
      className={`newtons-cradle ${className}`.trim()}
      style={{ "--uib-size": `${size}px`, "--uib-speed": speed, "--uib-color": color }}
      role="status"
      aria-label={label}
    >
      <span className="newtons-cradle__dot" />
      <span className="newtons-cradle__dot" />
      <span className="newtons-cradle__dot" />
      <span className="newtons-cradle__dot" />
    </span>
  );
}
