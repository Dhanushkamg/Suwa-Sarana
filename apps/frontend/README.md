This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Talking to the backend

The API base URL comes from `NEXT_PUBLIC_API_URL` (it includes `/api`). Copy `.env.example` to `.env.local`.
The committed example points at `http://localhost:8081/api`: on the development machine the Spring Boot backend runs on
port **8081** because Apache owns 8080 (`docs/LOCAL-RUN.md`, `apps/backend/run-integ.sh`). If your backend uses its default
port, set `NEXT_PUBLIC_API_URL=http://localhost:8080/api`. No port is hard-coded in the source: `src/lib/constants.ts`
reads the variable (its fallback is only the backend's default port) and `next.config.ts` derives the CSP `connect-src` from it.

The backend must list the frontend origin in `CORS_ALLOWED_ORIGIN` (`http://localhost:3000` in dev). The contract is in
`docs/API-CONTRACT.md`; error codes are translated through `authFlow.errors.<code>` (see `src/lib/apiError.ts`).

Checks: `npx tsc --noEmit`, `npx eslint src`, `npx vitest run`.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
