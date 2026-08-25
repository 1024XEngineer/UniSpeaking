export type AsyncTask<T> = {
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  result?: T | null;
  failureReason?: string | null;
};

const POLL_INTERVAL_MS = 1_000;
const TIMEOUT_MS = 180_000;

function wait(milliseconds: number) {
  return new Promise<void>((resolve) => globalThis.setTimeout(resolve, milliseconds));
}

export async function waitForAsyncTask<T>(
  initialTask: unknown,
  loadTask: () => Promise<unknown>,
  taskName: string,
): Promise<T> {
  let task = initialTask as Partial<AsyncTask<T>> | null;
  const deadline = Date.now() + TIMEOUT_MS;
  while (task?.status === 'PROCESSING') {
    if (Date.now() >= deadline) {
      throw new Error(`${taskName}等待超时，请稍后重试`);
    }
    await wait(POLL_INTERVAL_MS);
    task = (await loadTask()) as Partial<AsyncTask<T>> | null;
  }
  if (task?.status === 'FAILED') {
    throw new Error(task.failureReason || `${taskName}失败，请稍后重试`);
  }
  if (task?.status !== 'COMPLETED' || !task.result) {
    throw new Error(`${taskName}任务状态异常`);
  }
  return task.result;
}
