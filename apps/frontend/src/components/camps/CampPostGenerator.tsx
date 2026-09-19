'use client';

import { useRef, useState } from 'react';
import * as htmlToImage from 'html-to-image';
import { Download, Loader2, Droplets, Heart, Calendar, MapPin, Clock, Share2, Users } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useI18n } from '@/lib/i18n';

interface CampPostProps {
  camp: {
    name: string;
    district: string;
    location: string;
    scheduledDate: string;
    startTime: string;
    endTime: string;
    organizerName: string;
    requiredBloodGroups?: string;
  };
  onClose: () => void;
}

export default function CampPostGenerator({ camp, onClose }: CampPostProps) {
  const { t } = useI18n();
  const postRef = useRef<HTMLDivElement>(null);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async () => {
    if (!postRef.current) return;
    setDownloading(true);
    setError(null);
    try {
      // html-to-image needs the fonts/styles to be fully loaded
      const dataUrl = await htmlToImage.toJpeg(postRef.current, {
        quality: 0.95,
        backgroundColor: '#ffffff',
        width: 1080,
        height: 1080, // Instagram standard square size
        style: {
          transform: 'scale(1)',
          transformOrigin: 'top left',
          margin: '0',
        },
      });

      // Create dummy link to trigger download
      const link = document.createElement('a');
      link.download = `blood_drive_${camp.scheduledDate}.jpg`;
      link.href = dataUrl;
      link.click();
    } catch (err) {
      console.error('Failed to generate image', err);
      setError('Failed to generate poster. Please try again.');
    } finally {
      setDownloading(false);
    }
  };

  const bloodGroups = camp.requiredBloodGroups 
    ? camp.requiredBloodGroups.split(',').map(s => s.trim())
    : ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-[#0a0a0f] border border-white/10 rounded-3xl p-6 w-full max-w-5xl flex flex-col md:flex-row gap-8 items-start my-8">
        
        {/* Left Column: Post Preview */}
        <div className="flex-1 w-full bg-white rounded-xl overflow-hidden shadow-2xl relative shrink-0" style={{ maxWidth: '1080px', aspectRatio: '1/1' }}>
          
          {/* 
            This is the DOM node that gets captured. 
            We force it to 1080x1080 so the downloaded JPG is high-res,
            while using CSS scaling to fit it inside the preview window.
          */}
          <div className="w-full h-full relative overflow-hidden bg-white flex flex-col justify-between">
            <div 
              ref={postRef}
              className="absolute inset-0 bg-white"
              style={{ width: 1080, height: 1080, transformOrigin: 'top left', zoom: 'var(--preview-zoom, 1)' }}
            >
              {/* Background Elements */}
              <div className="absolute top-0 right-0 w-[600px] h-[600px] bg-gradient-to-bl from-red-50 to-white rounded-full -translate-y-1/4 translate-x-1/4" />
              <div className="absolute bottom-0 left-0 w-full h-[300px] bg-gradient-to-t from-red-600 via-red-500 to-transparent" />
              
              <div className="absolute inset-0 p-12 flex flex-col h-full z-10">
                {/* Header */}
                <div className="flex justify-between items-start">
                  <div className="flex items-center gap-4">
                    <div className="w-20 h-20 rounded-2xl bg-red-600 flex items-center justify-center shadow-xl">
                      <Heart className="w-10 h-10 text-white fill-white" />
                    </div>
                    <div>
                      <h2 className="text-gray-900 font-bold text-3xl tracking-tight">Suwa Sarana</h2>
                      <p className="text-gray-500 font-medium text-xl">National Blood Drive</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-red-600 font-black text-2xl uppercase tracking-widest">{camp.organizerName.split('@')[0]}</p>
                    <p className="text-gray-500 font-semibold text-lg uppercase tracking-wider">Presents</p>
                  </div>
                </div>

                {/* Hero Text */}
                <div className="mt-16 max-w-2xl">
                  <h1 className="text-[90px] font-black text-gray-900 leading-[0.95] tracking-tighter">
                    ONE DONATION <br/>
                    <span className="text-red-600">MANY</span> <br/>
                    <span className="font-[cursive] text-red-600 text-[110px] font-medium italic -ml-4 leading-[0.8]">Heartbeats</span>
                  </h1>
                  <p className="text-gray-600 text-2xl font-medium mt-10 leading-relaxed max-w-xl">
                    Your generosity can make a life-saving difference. Join our upcoming blood donation camp and be a hero to someone in need.
                  </p>
                </div>

                {/* Info Cards */}
                <div className="mt-auto mb-32 grid grid-cols-3 gap-6 relative z-20">
                  <div className="bg-white rounded-2xl shadow-xl shadow-red-900/5 p-6 border-l-8 border-red-500 flex flex-col justify-center">
                    <div className="flex items-center gap-3 mb-2">
                      <Calendar className="w-8 h-8 text-red-500" />
                      <span className="text-gray-500 font-bold text-xl uppercase">Date</span>
                    </div>
                    <p className="text-gray-900 font-black text-2xl">{new Date(camp.scheduledDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>
                  </div>

                  <div className="bg-white rounded-2xl shadow-xl shadow-red-900/5 p-6 border-l-8 border-amber-500 flex flex-col justify-center">
                    <div className="flex items-center gap-3 mb-2">
                      <Clock className="w-8 h-8 text-amber-500" />
                      <span className="text-gray-500 font-bold text-xl uppercase">Time</span>
                    </div>
                    <p className="text-gray-900 font-black text-2xl">{camp.startTime} - {camp.endTime}</p>
                  </div>

                  <div className="bg-white rounded-2xl shadow-xl shadow-red-900/5 p-6 border-l-8 border-emerald-500 flex flex-col justify-center">
                    <div className="flex items-center gap-3 mb-2">
                      <MapPin className="w-8 h-8 text-emerald-500" />
                      <span className="text-gray-500 font-bold text-xl uppercase">Location</span>
                    </div>
                    <p className="text-gray-900 font-black text-2xl truncate" title={camp.location}>{camp.location}</p>
                  </div>
                </div>

                {/* Footer Bar */}
                <div className="absolute bottom-0 left-0 w-full h-[140px] flex items-center justify-between px-16 z-30">
                  <div className="flex items-center gap-8">
                    <div className="flex -space-x-3">
                      {bloodGroups.slice(0, 4).map(bg => (
                        <div key={bg} className="w-16 h-16 rounded-full bg-white shadow-lg border-4 border-red-500 flex items-center justify-center font-black text-red-600 text-xl z-10 relative">
                          {bg}
                        </div>
                      ))}
                      {bloodGroups.length > 4 && (
                        <div className="w-16 h-16 rounded-full bg-red-100 shadow-lg border-4 border-white flex items-center justify-center font-black text-red-800 text-lg z-0 relative">
                          +{bloodGroups.length - 4}
                        </div>
                      )}
                    </div>
                    <div className="text-white">
                      <p className="font-bold text-2xl">Required Blood Types</p>
                      <p className="text-red-100 text-lg">Every drop counts</p>
                    </div>
                  </div>

                  <div className="bg-white rounded-full px-8 py-4 flex items-center gap-4 shadow-2xl shadow-red-900/50 transform translate-y-[-20px]">
                    <Droplets className="w-10 h-10 text-red-600" />
                    <div>
                      <p className="text-gray-900 font-black text-3xl tracking-tight">BE A LIFESAVER.</p>
                      <p className="text-red-600 font-bold text-xl uppercase tracking-wider">Book your slot now</p>
                    </div>
                  </div>
                </div>

                {/* Aesthetic Blood Drop Overlay */}
                <div className="absolute top-1/2 right-12 transform -translate-y-[60%] z-0 pointer-events-none opacity-90">
                  <svg width="400" height="550" viewBox="0 0 100 140" fill="none" xmlns="http://www.w3.org/2000/svg" className="drop-shadow-2xl">
                    <path d="M50 0C50 0 10 50 10 90C10 112.091 27.9086 130 50 130C72.0914 130 90 112.091 90 90C90 50 50 0 50 0Z" fill="url(#paint0_linear)"/>
                    <path d="M50 0C50 0 10 50 10 90C10 112.091 27.9086 130 50 130C72.0914 130 90 112.091 90 90C90 50 50 0 50 0Z" fill="url(#paint1_radial)" fillOpacity="0.4"/>
                    <defs>
                      <linearGradient id="paint0_linear" x1="50" y1="0" x2="50" y2="130" gradientUnits="userSpaceOnUse">
                        <stop stopColor="#ef4444"/>
                        <stop offset="1" stopColor="#991b1b"/>
                      </linearGradient>
                      <radialGradient id="paint1_radial" cx="0" cy="0" r="1" gradientUnits="userSpaceOnUse" gradientTransform="translate(30 80) rotate(45) scale(80)">
                        <stop stopColor="white" stopOpacity="0.8"/>
                        <stop offset="1" stopColor="white" stopOpacity="0"/>
                      </radialGradient>
                    </defs>
                  </svg>
                </div>
              </div>
            </div>
            
            {/* Embedded CSS to handle scaling the 1080x1080 node inside the container */}
            <style jsx>{`
              .preview-container {
                container-type: inline-size;
              }
              @container (max-width: 1080px) {
                :global(#qr-reader) { /* not related, just scoped */ }
              }
            `}</style>
          </div>
        </div>

        {/* Right Column: Controls */}
        <div className="w-full md:w-96 flex flex-col gap-6 shrink-0">
          <div>
            <h3 className="text-2xl font-bold text-white mb-2">{t('modals.posterTitle')}</h3>
            <p className="text-gray-400 text-sm leading-relaxed">
              {t('modals.posterDesc')}
            </p>
          </div>

          <div className="bg-white/5 border border-white/10 rounded-2xl p-5 space-y-4">
            <h4 className="text-white font-semibold text-sm uppercase tracking-wider mb-2">{t('modals.campaignDetails')}</h4>
            
            <div className="flex items-start gap-3">
              <MapPin className="w-5 h-5 text-gray-500 shrink-0" />
              <div>
                <p className="text-gray-300 font-medium text-sm">{camp.name}</p>
                <p className="text-gray-500 text-xs">{camp.location}</p>
              </div>
            </div>
            
            <div className="flex items-start gap-3">
              <Calendar className="w-5 h-5 text-gray-500 shrink-0" />
              <div>
                <p className="text-gray-300 font-medium text-sm">{camp.scheduledDate}</p>
                <p className="text-gray-500 text-xs">{camp.startTime} - {camp.endTime}</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Droplets className="w-5 h-5 text-gray-500 shrink-0" />
              <div className="flex flex-wrap gap-1 mt-0.5">
                {bloodGroups.map(bg => (
                  <span key={bg} className="px-2 py-0.5 rounded-full bg-red-500/10 text-red-400 border border-red-500/20 text-[10px] font-bold">
                    {bg}
                  </span>
                ))}
              </div>
            </div>
          </div>

          {error && (
            <p className="text-sm text-red-400 font-medium p-3 bg-red-500/10 rounded-xl border border-red-500/20">
              {error}
            </p>
          )}

          <div className="mt-auto space-y-3">
            <Button 
              className="w-full bg-gradient-to-r from-red-600 to-rose-600 hover:from-red-500 hover:to-rose-500 text-white font-bold h-12 text-lg shadow-xl shadow-red-900/30"
              onClick={handleDownload}
              loading={downloading}
            >
              {downloading ? (
                <>{t('modals.generatingJpg')}</>
              ) : (
                <>
                  <Download className="w-5 h-5 mr-2" /> {t('modals.downloadPoster')}
                </>
              )}
            </Button>
            
            <Button 
              variant="secondary" 
              className="w-full h-12 bg-white/10 hover:bg-white/20 text-white border-white/20"
              onClick={onClose}
              disabled={downloading}
            >
              {t('common.cancel')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
