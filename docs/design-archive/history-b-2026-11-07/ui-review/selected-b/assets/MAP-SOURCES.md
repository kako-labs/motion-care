# Map and route snapshot · 2026-11-08

`route-map-data.js` is an OpenStreetMap-derived geographic data extract, licensed under **ODbL 1.0**. Attribution: **© OpenStreetMap contributors**. [Copyright and license](https://www.openstreetmap.org/copyright) · [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/).

The map and every route use one Web Mercator projection in `mapSvg()`. Routes are polylines returned by OSRM, never hand-drawn Bezier shortcuts. Roads, parks, land use and waterways are from Overpass. Open waterways are rendered as lines, not filled polygons. No rendered map tiles were downloaded or repackaged.

## Reproduction

- Routing endpoint: `https://router.project-osrm.org/route/v1/driving/127.0355,37.4840;127.0609,37.5141?alternatives=true&steps=true&geometries=geojson&overview=full&annotations=nodes,distance,duration`
- Map endpoint: `https://overpass-api.de/api/interpreter`
- Bounding box, south/west/north/east: `37.480599,127.02373,37.517004,127.067475`
- Query: ways with highway types motorway/trunk/primary/secondary/tertiary/residential/unclassified/living_street/service and their links; park, natural=water, waterway, landuse ways; traffic_calming nodes. `out geom`. The packaged extract contains the geometries and a reduced set of source tags; node proximity is not used to infer route-level absence of bumps.
- Endpoints are public places near Yangjae and Bongeunsa stations, not a user's location.

Source request URL, retrieval timestamp and attribution are also embedded in the data. Reuse of the geographic extract remains subject to ODbL. The UI code and synthetic journey replay are distinct from the geographic database.

## What the snapshot means

OSRM route 0: 5.8km, 2 explicit left/right junction maneuvers. Route 1: 5.6km, 4 such maneuvers. Displayed durations are rounded up from the response; they do not include live traffic. The comparison counts `turn`, `end of road`, `roundabout`, `rotary`, excluding straight and slight modifiers. It does not count all curvature, prove fewer sharp bends, certify smooth pavement, or provide Pleos COMFORTABLE results.

This is a saved route response for a UI demonstration, not a live navigation service. Selection changes local UI state only. No vehicle or navigation SDK was modified.
