// A simplified Kenya outline (longitude, latitude), good enough for a projector. Not survey-accurate.
export const KENYA_OUTLINE: [number, number][] = [
  [34.0, 4.2], [34.4, 4.6], [35.9, 5.0], [36.0, 4.4], [37.1, 4.3], [38.1, 3.6], [39.0, 3.5], [39.8, 3.9],
  [41.0, 3.95], [41.9, 3.98], [41.3, 3.0], [40.9, 2.0], [41.0, -0.8], [41.5, -1.7], [40.9, -2.1], [40.2, -2.8],
  [39.7, -4.05], [39.2, -4.67], [38.6, -4.0], [37.7, -3.1], [37.6, -3.5], [36.7, -3.0], [35.2, -2.0],
  [34.0, -1.0], [33.95, 0.1], [34.1, 0.5], [34.6, 1.1], [34.9, 1.9], [34.6, 3.0],
];

export const MAP_W = 520;
export const MAP_H = 600;
const LON_MIN = 33.6, LON_MAX = 42.2, LAT_MIN = -5.0, LAT_MAX = 5.4;

export function project(lon: number, lat: number): [number, number] {
  return [((lon - LON_MIN) / (LON_MAX - LON_MIN)) * MAP_W, ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN)) * MAP_H];
}

export const OUTLINE_PATH =
  KENYA_OUTLINE.map(([lon, lat], i) => {
    const [x, y] = project(lon, lat);
    return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(" ") + " Z";

export const TOWN_LABELS: { name: string; lon: number; lat: number }[] = [
  { name: "Nairobi", lon: 36.82, lat: -1.29 }, { name: "Mombasa", lon: 39.67, lat: -4.04 },
  { name: "Kisumu", lon: 34.77, lat: -0.09 }, { name: "Eldoret", lon: 35.27, lat: 0.51 },
];
