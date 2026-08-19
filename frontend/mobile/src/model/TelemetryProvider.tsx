import { type PropsWithChildren, useEffect } from 'react';

import { mobileTelemetry } from '@/infrastructure/telemetry/MobileTelemetry';

import { useAppModel } from './AppModel';

export function TelemetryProvider({ children }: PropsWithChildren) {
  const { userId } = useAppModel();

  useEffect(() => {
    mobileTelemetry.setUser(userId);
  }, [userId]);

  return children;
}
