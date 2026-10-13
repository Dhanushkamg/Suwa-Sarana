import { LandingNav } from '@/components/landing/LandingNav';
import { Hero } from '@/components/landing/Hero';
import { BloodTypeStrip } from '@/components/landing/BloodTypeStrip';
import { HowItWorks } from '@/components/landing/HowItWorks';
import { Features } from '@/components/landing/Features';
import { StatsBand } from '@/components/landing/StatsBand';
import { ClosingCta } from '@/components/landing/ClosingCta';
import { LandingFooter } from '@/components/landing/LandingFooter';

// Public landing page. Each section is a client component (i18n + motion); this shell stays a server component.
export default function Home() {
  return (
    <div className="min-h-screen overflow-x-clip bg-background text-foreground">
      <LandingNav />
      <main id="main">
        <Hero />
        <BloodTypeStrip />
        <HowItWorks />
        <Features />
        <StatsBand />
        <ClosingCta />
      </main>
      <LandingFooter />
    </div>
  );
}
