import { useEffect, useState } from 'react';
import { useAuth } from './auth/useAuth';
import apiClient from './api/axios';

interface SessionData {
  authenticated: boolean;
  subject: string;
  username: string | null;
}

function App() {
  const { isAuthenticated, isInitializing, login, logout } = useAuth();
  const [session, setSession] = useState<SessionData | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) {
      setSession(null);
      return;
    }

    apiClient
      .get<{ data: SessionData }>('/auth/session')
      .then((response) => setSession(response.data.data))
      .catch(() => setSessionError('Không thể tải thông tin session.'));
  }, [isAuthenticated]);

  if (isInitializing) {
    return <p>Đang kiểm tra đăng nhập...</p>;
  }

  if (!isAuthenticated) {
    return (
      <div>
        <p>Bạn chưa đăng nhập.</p>
        <button type="button" onClick={login}>
          Đăng nhập
        </button>
      </div>
    );
  }

  return (
    <div>
      <p>
        Xin chào, {session?.username ?? '...'}
      </p>
      {sessionError && <p role="alert">{sessionError}</p>}
      <button type="button" onClick={logout}>
        Đăng xuất
      </button>
    </div>
  );
}

export default App;
