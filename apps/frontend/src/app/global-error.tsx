'use client';

// Last-resort boundary for errors in the root layout itself. It replaces the whole document,
// so it cannot use the app's providers or stylesheet — plain inline styles only.
export default function GlobalError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, background: '#0d0d14', color: '#f0f0f5', fontFamily: 'system-ui, sans-serif' }}>
        <main style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16, padding: 24, textAlign: 'center' }}>
          <h1 style={{ fontSize: 24, margin: 0 }}>Something went wrong</h1>
          <p style={{ margin: 0, color: '#9ca3af' }}>Please try again. If the problem continues, come back in a few minutes.</p>
          <button onClick={() => retry()} style={{ background: '#ef4444', color: '#fff', border: 0, borderRadius: 12, padding: '10px 20px', fontWeight: 600, cursor: 'pointer' }}>
            Try again
          </button>
        </main>
      </body>
    </html>
  );
}
