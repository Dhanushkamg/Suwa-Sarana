'use client';

import { useState, useEffect } from 'react';
import apiClient from '@/lib/apiClient';
import { Activity } from 'lucide-react';

interface StockSignal {
  hospitalName: string;
  district: string;
  bloodType: string;
  status: 'LOW' | 'CRITICAL';
}

export function StockTicker() {
  const [signals, setSignals] = useState<StockSignal[]>([]);

  useEffect(() => {
    const fetchSignals = async () => {
      try {
        const response = await apiClient.get('/inventory/stock-signals');
        setSignals(response.data);
      } catch (error) {
        console.error('Failed to load stock signals', error);
      }
    };
    fetchSignals();
    
    // Refresh every 5 minutes
    const interval = setInterval(fetchSignals, 300000);
    return () => clearInterval(interval);
  }, []);

  if (signals.length === 0) return null;

  // Duplicate signals to create a seamless loop
  const loopSignals = [...signals, ...signals];

  return (
    <div className="bg-red-950/30 border-y border-red-900/50 py-3 overflow-hidden flex whitespace-nowrap group">
      <div className="flex w-max animate-marquee-horizontal gap-8 items-center px-4 group-hover:hover-pause shrink-0">
        {loopSignals.map((sig, idx) => (
          <div key={idx} className="flex items-center gap-2 text-sm shrink-0">
            <Activity className="w-4 h-4 text-red-500 animate-pulse shrink-0" />
            <span className="font-semibold text-white">{sig.hospitalName} ({sig.district})</span>
            <span className="text-gray-400">needs</span>
            <span className="font-bold text-red-400">{sig.bloodType}</span>
            <span className="px-2 py-0.5 rounded text-xs font-bold bg-red-500/20 text-red-400 border border-red-500/30 shrink-0">
              {sig.status}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
