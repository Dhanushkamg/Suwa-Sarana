export type ShareChannelId = 'whatsapp' | 'facebook' | 'telegram' | 'viber';

export interface ShareChannel {
  id: ShareChannelId;
  /** Brand names are not translated */
  name: string;
  href: string;
}

/** Plain share links. They carry the text and the link only: a web link cannot attach the downloaded image. */
export function shareChannels(text: string, url: string): ShareChannel[] {
  const both = encodeURIComponent(`${text}\n\n${url}`);
  return [
    { id: 'whatsapp', name: 'WhatsApp', href: `https://api.whatsapp.com/send?text=${both}` },
    { id: 'facebook', name: 'Facebook', href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}` },
    { id: 'telegram', name: 'Telegram', href: `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}` },
    { id: 'viber', name: 'Viber', href: `viber://forward?text=${both}` },
  ];
}
