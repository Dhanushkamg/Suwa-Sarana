'use client';

import { useState } from 'react';
import { Mail, Phone, MapPin, ArrowUpRight } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Reveal } from '@/components/motion/Reveal';
import { Alert } from '@/components/pub/Alert';
import { PubFooter } from '@/components/pub/PubFooter';
import { PubHeader } from '@/components/pub/PubHeader';
import { Textarea } from '@/components/pub/Textarea';

export default function ContactPage() {
  const { t } = useI18n();
  const [formData, setFormData] = useState({ name: '', email: '', subject: '', message: '' });
  const [loading, setLoading] = useState(false);
  const [submitStatus, setSubmitStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [submitMessage, setSubmitMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSubmitStatus('idle');
    try {
      await apiClient.post('/contact', formData);
      setSubmitStatus('success');
      setSubmitMessage(t('contact.successMessage'));
      setFormData({ name: '', email: '', subject: '', message: '' });
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
      setSubmitStatus('error');
      setSubmitMessage(typeof message === 'string' && message ? message : t('contact.errorMessage'));
    } finally {
      setLoading(false);
    }
  };

  const channels = [
    {
      icon: Mail,
      label: t('contact.email'),
      value: 'support@suwasarana.lk',
      href: 'mailto:support@suwasarana.lk',
      note: t('pub.contactReply'),
    },
    {
      icon: Phone,
      label: t('contact.hotline'),
      value: '+94 11 234 5678',
      href: 'tel:+94112345678',
      note: t('contact.hours'),
    },
    {
      icon: MapPin,
      label: t('contact.locationTitle'),
      value: t('contact.locationDesc'),
      note: undefined,
      href: undefined,
    },
  ];

  return (
    <div className="min-h-screen bg-background text-foreground">
      <PubHeader />

      <main className="mx-auto max-w-6xl px-5 pb-24 pt-12 sm:px-8 sm:pt-20">
        <div className="grid gap-14 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:gap-20">
          {/* Left: editorial intro + channels */}
          <Reveal y={20}>
            <p className="eyebrow mb-5 flex items-center gap-2.5">
              <span aria-hidden="true" className="h-px w-8 bg-ember" />
              {t('pub.contactEyebrow')}
            </p>
            <h1 className="font-display break-words text-5xl text-foreground sm:text-6xl lg:text-7xl">{t('contact.title')}</h1>
            <p className="mt-6 max-w-md text-base leading-relaxed text-gray-400 sm:text-lg">{t('contact.subtitle')}</p>

            <ul className="mt-12 border-t border-line">
              {channels.map((item) => {
                const Icon = item.icon;
                const body = (
                  <>
                    <p className="eyebrow flex items-center gap-2.5">
                      <Icon className="h-3.5 w-3.5 text-ember" aria-hidden="true" />
                      {item.label}
                    </p>
                    <div className="mt-2 flex items-start justify-between gap-4">
                      <span className="min-w-0 break-words font-display text-2xl text-foreground sm:text-3xl">{item.value}</span>
                      {item.href && (
                        <ArrowUpRight
                          className="mt-1.5 h-5 w-5 shrink-0 text-gray-600 transition-[transform,color] duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-ember"
                          aria-hidden="true"
                        />
                      )}
                    </div>
                    {item.note && <p className="mt-1.5 text-sm text-gray-500">{item.note}</p>}
                  </>
                );
                return (
                  <li key={item.label} className="border-b border-line">
                    {item.href ? (
                      <a href={item.href} className="group block py-6 transition-colors hover:bg-foreground/[0.02]">
                        {body}
                      </a>
                    ) : (
                      <div className="py-6">{body}</div>
                    )}
                  </li>
                );
              })}
            </ul>
          </Reveal>

          {/* Right: form */}
          <Reveal y={28} delay={0.1}>
            <div className="glass-card rounded-3xl p-6 sm:p-10">
              <h2 className="font-display text-3xl text-foreground sm:text-4xl">{t('contact.formTitle')}</h2>
              <form onSubmit={handleSubmit} className="mt-8 space-y-5">
                {submitStatus === 'success' && <Alert tone="success">{submitMessage}</Alert>}
                {submitStatus === 'error' && <Alert>{submitMessage}</Alert>}

                <div className="grid gap-5 sm:grid-cols-2">
                  <Input
                    id="contact-name"
                    type="text"
                    required
                    label={t('contact.nameLabel')}
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    placeholder={t('contact.namePlaceholder')}
                  />
                  <Input
                    id="contact-email"
                    type="email"
                    required
                    label={t('contact.emailLabel')}
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    placeholder={t('contact.emailPlaceholder')}
                  />
                </div>
                <Input
                  id="contact-subject"
                  type="text"
                  required
                  label={t('contact.subjectLabel')}
                  value={formData.subject}
                  onChange={(e) => setFormData({ ...formData, subject: e.target.value })}
                  placeholder={t('contact.subjectPlaceholder')}
                />
                <Textarea
                  id="contact-message"
                  rows={6}
                  required
                  label={t('contact.messageLabel')}
                  value={formData.message}
                  onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                  placeholder={t('contact.messagePlaceholder')}
                />
                <div className="pt-2">
                  <Button type="submit" size="lg" className="w-full sm:w-auto sm:min-w-48" loading={loading}>
                    {loading ? t('contact.sending') : t('contact.sendBtn')}
                  </Button>
                </div>
              </form>
            </div>
          </Reveal>
        </div>
      </main>

      <PubFooter current="contact" />
    </div>
  );
}
