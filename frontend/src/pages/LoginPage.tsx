import { useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { login, register, error, busy, clearError } = useAuth();

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  // Switching modes clears the error, otherwise the message from a
  // failed login stays on screen after the user switches to register.
  useEffect(() => {
    clearError();
  }, [mode, clearError]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (mode === 'login') {
      await login(email, password);
    } else {
      await register(fullName, email, password);
    }
  }

  const isRegister = mode === 'register';

  return (
    <div className="auth-wrap">
      <div className="card auth-card">
        <h1>
          Campus<span>Pilot</span>
        </h1>
        <p className="subtitle">
          {isRegister
            ? 'Create your student account'
            : 'Sign in to track your attendance'}
        </p>

        {error && (
          <div className="alert alert--error" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={onSubmit} noValidate>
          {isRegister && (
            <div className="field">
              <label htmlFor="fullName">Full name</label>
              <input
                id="fullName"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                autoComplete="name"
                required
              />
            </div>
          )}

          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              required
            />
            {isRegister && (
              <p className="field-hint">At least 10 characters.</p>
            )}
          </div>

          <button type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy
              ? 'Please wait…'
              : isRegister
                ? 'Create account'
                : 'Sign in'}
          </button>
        </form>

        <p className="auth-switch">
          {isRegister ? 'Already registered? ' : 'No account yet? '}
          <button
            type="button"
            className="link-btn"
            onClick={() => setMode(isRegister ? 'login' : 'register')}
          >
            {isRegister ? 'Sign in' : 'Create one'}
          </button>
        </p>
      </div>
    </div>
  );
}