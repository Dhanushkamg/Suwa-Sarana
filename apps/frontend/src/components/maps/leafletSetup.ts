import type * as Leaflet from 'leaflet';
import { SRI_LANKA_BOUNDS, TILE_ATTRIBUTION, TILE_URL } from './mapConfig';
import { ensureMapStyles } from './mapStyles';

type L = typeof Leaflet;

export interface MapLabels {
  zoomIn: string;
  zoomOut: string;
}

/**
 * Creates a Sri Lanka map: OSM tiles with visible attribution, translated zoom buttons, panning locked to the
 * island (viscosity 1), the whole island fitted into view, and zoom-out limited to that fit so neighbouring
 * India is neither shown nor reachable. Tiles outside the bounds are not requested at all.
 * Returns the map plus a `refit()` to call when the container is resized.
 */
export function createSriLankaMap(L: L, el: HTMLElement, labels: MapLabels, options: { maxZoom?: number } = {}) {
  ensureMapStyles();
  const bounds = L.latLngBounds(SRI_LANKA_BOUNDS);

  const map = L.map(el, {
    zoomControl: false,
    attributionControl: false,
    zoomSnap: 0.25,
    maxZoom: options.maxZoom ?? 12,
    maxBounds: bounds,
    maxBoundsViscosity: 1.0,
  });

  // `dark-map-tiles` is only styled under the dark theme (see globals.css)
  L.tileLayer(TILE_URL, {
    maxZoom: 19,
    bounds,
    noWrap: true,
    className: 'dark-map-tiles',
    attribution: TILE_ATTRIBUTION,
  }).addTo(map);

  L.control.zoom({ position: 'topleft', zoomInTitle: labels.zoomIn, zoomOutTitle: labels.zoomOut }).addTo(map);
  L.control.attribution({ position: 'bottomright', prefix: '<a href="https://leafletjs.com" target="_blank" rel="noopener noreferrer">Leaflet</a>' }).addTo(map);

  const refit = (fit: boolean) => {
    map.invalidateSize({ animate: false });
    const size = map.getSize();
    if (size.x === 0 || size.y === 0) return;
    const fitted = map.getBoundsZoom(bounds, false);
    map.setMinZoom(fitted);
    if (fit) map.fitBounds(bounds, { animate: false });
    else if (map.getZoom() < fitted) map.setZoom(fitted, { animate: false });
    map.panInsideBounds(bounds, { animate: false });
  };
  refit(true);

  // Marker labels crowd together when the whole island is in view: hide the optional ones until the user zooms in
  const syncZoomClass = () => el.classList.toggle('sl-zoomed-out', map.getZoom() < 9);
  map.on('zoomend', syncZoomClass);
  syncZoomClass();

  return { map, bounds, refit };
}
