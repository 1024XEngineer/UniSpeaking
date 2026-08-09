import { X } from "@phosphor-icons/react";

const cx = (...parts) => parts.filter(Boolean).join(" ");

export function Modal({ children, onClose, wide = false, dismissible = true, className }) {
  return <div className="modal-backdrop" onMouseDown={dismissible ? onClose : undefined}><div className={cx("modal", wide && "modal--wide", className)} role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>{dismissible && <button className="modal__close" aria-label="关闭" onClick={onClose}><X /></button>}{children}</div></div>;
}
