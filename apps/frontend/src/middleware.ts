import createMiddleware from 'next-intl/middleware';

export default createMiddleware({
  locales: ['en', 'si', 'ta'],
  defaultLocale: 'en',
  localePrefix: 'as-needed', // /en is omitted, /si and /ta are prefixed
});

export const config = {
  matcher: [
    // Match all pathnames except for Next.js internals and static files
    '/((?!_next|_vercel|.*\\..*).*)',
  ],
};
