/** Public origin of the website, used for canonical/Open Graph URLs, sitemap and robots. */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000').replace(/\/$/, '');

/**
 * Base URL of the REST API, including "/api". Set NEXT_PUBLIC_API_URL (see .env.example); the fallback is only the
 * backend's own default port. Which port the local backend really uses is an environment matter: see README / docs/LOCAL-RUN.md.
 */
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');

export const BLOOD_TYPES = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'] as const;
export type BloodType = typeof BLOOD_TYPES[number];
