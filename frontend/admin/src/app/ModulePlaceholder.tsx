interface ModulePlaceholderProps {
  eyebrow: string
  title: string
  description: string
}

export function ModulePlaceholder({ eyebrow, title, description }: ModulePlaceholderProps) {
  return (
    <section className="module-placeholder glass-surface">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p>{description}</p>
      <span className="quiet-badge">接口已预留 · 后续阶段接入</span>
    </section>
  )
}
