import type { NextConfig } from 'next';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
// Extract just the origin (scheme + host) for CSP connect-src
const apiOrigin = (() => {
  try {
    return new URL(API_URL).origin;
  } catch {
    return API_URL;
  }
})();

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        // Apply security headers to all routes
        source: '/(.*)',
        headers: [
          // ---------------------------------------------------------------
          // Prevent clickjacking: this page may not be embedded in a frame.
          // ---------------------------------------------------------------
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          // ---------------------------------------------------------------
          // Stop browsers from MIME-sniffing the Content-Type.
          // ---------------------------------------------------------------
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          // ---------------------------------------------------------------
          // Limit information in the Referer header sent to external sites.
          // ---------------------------------------------------------------
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
          // ---------------------------------------------------------------
          // Enforce HTTPS for 1 year (production only — harmless in dev).
          // includeSubDomains and preload for maximum protection.
          // ---------------------------------------------------------------
          {
            key: 'Strict-Transport-Security',
            value: 'max-age=31536000; includeSubDomains; preload',
          },
          // ---------------------------------------------------------------
          // Permissions Policy: disable browser features the app doesn't use.
          // ---------------------------------------------------------------
          {
            key: 'Permissions-Policy',
            value: [
              'camera=()',
              'microphone=()',
              'payment=()',
              'usb=()',
              'geolocation=(self)', // Leaflet map may use geolocation
            ].join(', '),
          },
          // ---------------------------------------------------------------
          // Content Security Policy
          //
          // Directives are tuned to the app's actual requirements:
          //   - Google Fonts for Inter typeface
          //   - Leaflet tile servers for the map view
          //   - Backend API for data fetching
          //   - Next.js inline scripts (required by the framework)
          //
          // 'unsafe-inline' for style-src is required by Tailwind/CSS-in-JS.
          // 'unsafe-eval' is NOT included.
          // ---------------------------------------------------------------
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              // Next.js requires 'unsafe-inline' for its hydration scripts.
              // nonce-based CSP would be the next hardening step.
              "script-src 'self' 'unsafe-inline'",
              // Tailwind and CSS-in-JS require unsafe-inline for styles.
              "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
              // Google Fonts CDN for the Inter typeface
              "font-src 'self' https://fonts.gstatic.com",
              // Backend API + WebSocket for SSE notifications
              `connect-src 'self' ${apiOrigin}`,
              // App images + OpenStreetMap/Leaflet tile servers
              "img-src 'self' data: blob: https://*.tile.openstreetmap.org https://*.openstreetmap.org",
              // Leaflet renders maps in iframes on some versions
              "frame-src 'none'",
              "object-src 'none'",
              "base-uri 'self'",
              "form-action 'self'",
            ].join('; '),
          },
        ],
      },
    ];
  },
};

export default nextConfig;
