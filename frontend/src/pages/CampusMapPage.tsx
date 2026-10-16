import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  MapContainer,
  Marker,
  Polyline,
  Popup,
  TileLayer,
  useMap,
} from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { api } from '../api/client';
import type { CampusLocation } from '../api/types';

/**
 * OpenStreetMap tiles, not Google Maps.
 *
 * <p>Google's JavaScript API costs roughly $7 per 1,000 loads and needs a
 * billing card even for the free tier. That matters here twice over: the
 * cost scales with usage, and card details belong nowhere near a
 * repository. OSM tiles are free, need no key, and render identically at
 * campus scale.
 *
 * <p>The trade-off is tile styling and occasionally slower tile loading.
 * For a navigation map on one campus, that is a good deal.
 */
const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

/** Demo data sits around KL University, Guntur. */
const DEFAULT_CENTER: [number, number] = [16.3069, 80.4365];

const CATEGORY_META: Record<string, { label: string; color: string; icon: string }> = {
  academic: { label: 'Academic', color: '#1d4ed8', icon: 'M' },
  residential: { label: 'Residential', color: '#7c3aed', icon: 'H' },
  food: { label: 'Food', color: '#c2410c', icon: 'F' },
  sport: { label: 'Sports', color: '#15803d', icon: 'S' },
  admin: { label: 'Admin', color: '#475569', icon: 'A' },
  service: { label: 'Services', color: '#0e7490', icon: 'S' },
  other: { label: 'Other', color: '#64748b', icon: '•' },
};

/**
 * Leaflet's default marker images resolve against its own CSS path and
 * come out blank when bundled. This replaces them with inline SVG, which
 * bundles correctly and needs no network fetch.
 */
function pin(color: string, letter: string) {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="28" height="40" viewBox="0 0 28 40">
      <path d="M14 0C6.8 0 1 5.8 1 13c0 9.4 11.2 24.6 12.3 26.1a.9.9 0 0 0 1.4 0C15.8 37.6 27 22.4 27 13 27 5.8 21.2 0 14 0z"
            fill="${color}" stroke="#fff" stroke-width="2"/>
      <text x="14" y="17" font-family="sans-serif" font-size="12" font-weight="700"
            fill="#fff" text-anchor="middle">${letter}</text>
    </svg>`;
  return L.divIcon({
    html: svg,
    className: '',
    iconSize: [28, 40],
    iconAnchor: [14, 40],
    popupAnchor: [0, -36],
  });
}

/** Fits the map to every marker once they load. */
function FitBounds({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length > 1) {
      map.fitBounds(L.latLngBounds(points), { padding: [50, 50] });
    }
  }, [map, points]);
  return null;
}

export function CampusMapPage() {
  const [places, setPlaces] = useState<CampusLocation[] | null>(null);
  const [from, setFrom] = useState<CampusLocation | null>(null);
  const [to, setTo] = useState<CampusLocation | null>(null);
  const [route, setRoute] = useState<[number, number][] | null>(null);
  const [routeInfo, setRouteInfo] = useState<{ km: number; minutes: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setPlaces(await api.campusLocations());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load campus locations.');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const pick = (place: CampusLocation) => {
    // First tap sets the start, second sets the destination. Reusing a
    // single "select" concept avoids the user having to decide whether
    // they are choosing an origin or a destination before they know.
    if (!from || (from && to)) {
      setFrom(place);
      setTo(null);
      setRoute(null);
      setRouteInfo(null);
      return;
    }
    if (place.id === from.id) return;
    setTo(place);
    void findRoute(from, place);
  };

  async function findRoute(a: CampusLocation, b: CampusLocation) {
    setBusy(true);
    setError(null);
    try {
      const data = await api.campusRoute(
        a.latitude,
        a.longitude,
        b.latitude,
        b.longitude,
      );
      const legs = data.routes?.[0];
      const coords = (legs?.geometry?.coordinates ?? []).map(
        (c: number[]) => [c[1], c[0]] as [number, number],
      );
      setRoute(coords);
      setRouteInfo({
        km: legs?.distance ? legs.distance / 1000 : 0,
        minutes: legs?.duration ? Math.round(legs.duration / 60) : 0,
      });
    } catch {
      setError('Could not calculate a route. The routing service may be busy.');
      setRoute(null);
    } finally {
      setBusy(false);
    }
  }

  const markerPoints = useMemo<[number, number][]>(
    () => (places ?? []).map((p) => [p.latitude, p.longitude] as [number, number]),
    [places],
  );

  const grouped = useMemo(() => {
    const g: Record<string, CampusLocation[]> = {};
    for (const p of places ?? []) {
      (g[p.category] ??= []).push(p);
    }
    return g;
  }, [places]);

  return (
    <div className="page map-page">
      <div className="page-head">
        <div>
          <h1>Campus map</h1>
          <p className="subtitle">
            Tap a place to set your start, then a second place for directions.
          </p>
        </div>
        <button
          className="secondary"
          onClick={() => {
            setFrom(null);
            setTo(null);
            setRoute(null);
            setRouteInfo(null);
          }}
        >
          Clear
        </button>
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <MapContainer
          center={DEFAULT_CENTER}
          zoom={16}
          style={{ height: '440px', width: '100%' }}
          scrollWheelZoom={false}
        >
          <TileLayer url={TILE_URL} attribution={ATTRIBUTION} />
          <FitBounds points={markerPoints} />

          {places?.map((p) => {
            const meta = CATEGORY_META[p.category] ?? CATEGORY_META.other;
            const isFrom = from?.id === p.id;
            const isTo = to?.id === p.id;
            return (
              <Marker
                key={p.id}
                position={[p.latitude, p.longitude]}
                icon={pin(isFrom ? '#0f172a' : isTo ? '#15803d' : meta.color, meta.icon)}
                eventHandlers={{ click: () => pick(p) }}
              >
                <Popup>
                  <strong>{p.name}</strong>
                  {p.building && (
                    <>
                      <br />
                      {p.building}
                    </>
                  )}
                  {p.description && (
                    <>
                      <br />
                      {p.description}
                    </>
                  )}
                  <br />
                  <em>Tap to {from && !to ? 'set as destination' : 'set as start'}</em>
                </Popup>
              </Marker>
            );
          })}

          {route && (
            <Polyline
              positions={route}
              pathOptions={{ color: '#1d4ed8', weight: 5, opacity: 0.8 }}
            />
          )}
        </MapContainer>
      </div>

      {routeInfo && (
        <div className="alert alert--info" role="status" style={{ marginTop: '0.9rem' }}>
          {from?.name} &rarr; {to?.name}:{' '}
          <strong>{routeInfo.km.toFixed(2)} km</strong> · about{' '}
          <strong>{routeInfo.minutes} min</strong> walking
          {busy && ' · calculating…'}
        </div>
      )}

      <div className="card" style={{ marginTop: '1rem' }}>
        <h2>Quick destinations</h2>
        {!places && <p className="subtitle">Loading…</p>}

        {Object.entries(grouped).map(([cat, items]) => {
          const meta = CATEGORY_META[cat] ?? CATEGORY_META.other;
          return (
            <div key={cat} style={{ marginBottom: '1rem' }}>
              <div className="subject__code" style={{ marginBottom: '0.4rem' }}>
                {meta.label.toUpperCase()}
              </div>
              <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap' }}>
                {items.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    className="secondary small"
                    style={{ borderColor: from?.id === p.id ? '#0f172a' : undefined }}
                    onClick={() => pick(p)}
                  >
                    {p.name}
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      <p className="subtitle" style={{ marginTop: '1rem' }}>
        Map data &copy; OpenStreetMap contributors. Routing by OSRM. Both are
        free and need no API key — unlike Google Maps, which bills per load
        and requires a card.
      </p>
    </div>
  );
}