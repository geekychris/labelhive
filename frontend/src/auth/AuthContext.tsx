import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { api, setDebugUserId } from '../api/client';
import type { DebugUser, Me } from '../api/types';

interface Ctx {
  me?: Me;
  debugUsers: DebugUser[];
  switchDebugUser: (id: string) => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthCtx = createContext<Ctx>({ debugUsers: [], switchDebugUser: async () => {}, refresh: async () => {} });

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me>();
  const [debugUsers, setDebugUsers] = useState<DebugUser[]>([]);

  const refresh = async () => {
    const m = await api.me();
    setMe(m);
    if (m.debug) setDebugUsers(await api.debugUsers());
  };

  useEffect(() => { refresh().catch(console.error); }, []);

  const switchDebugUser = async (id: string) => {
    setDebugUserId(id);
    await refresh();
  };

  return <AuthCtx.Provider value={{ me, debugUsers, switchDebugUser, refresh }}>{children}</AuthCtx.Provider>;
}

export const useAuth = () => useContext(AuthCtx);
