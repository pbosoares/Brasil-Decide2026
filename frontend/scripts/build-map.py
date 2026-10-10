"""Derive SVG paths from the official simplified IBGE UF boundaries. No runtime map request."""
import json, math
from pathlib import Path

root = Path(__file__).resolve().parents[2]
geo = json.loads((root / 'frontend/scripts/source/brasil-uf.geojson').read_text(encoding='utf-8'))
codes = dict(zip(['11','12','13','14','15','16','17','21','22','23','24','25','26','27','28','29','31','32','33','35','41','42','43','50','51','52','53'], ['RO','AC','AM','RR','PA','AP','TO','MA','PI','CE','RN','PB','PE','AL','SE','BA','MG','ES','RJ','SP','PR','SC','RS','MS','MT','GO','DF']))
def rings(feature):
    geometry = feature['geometry']
    return geometry['coordinates'] if geometry['type'] == 'Polygon' else [ring for polygon in geometry['coordinates'] for ring in polygon]
points = [point for f in geo['features'] for ring in rings(f) for point in ring]
xmin, xmax = min(p[0] for p in points), max(p[0] for p in points)
ymin, ymax = min(p[1] for p in points), max(p[1] for p in points)
scale = min(545 / ((xmax-xmin)*math.cos(math.radians(15))), 530/(ymax-ymin))
def project(point):
    return [(point[0]-xmin)*math.cos(math.radians(15))*scale+25, (ymax-point[1])*scale+15]
def centroid(ring):
    area = cx = cy = 0
    for p,q in zip(ring, ring[1:]+ring[:1]):
        cross = p[0]*q[1]-q[0]*p[1]
        area += cross; cx += (p[0]+q[0])*cross; cy += (p[1]+q[1])*cross
    return abs(area), [cx/(3*area),cy/(3*area)] if area else ring[0]
output = {}
for f in geo['features']:
    rs = rings(f)
    label = max((centroid(r) for r in rs), key=lambda pair:pair[0])[1]
    path = ''.join('M'+'L'.join(','.join(f'{v:.1f}' for v in project(p)) for p in r)+'Z' for r in rs)
    output[codes[f['properties']['codarea']]] = {'path':path,'label':[round(v,1) for v in project(label)]}
assert len(output) == 27
(root/'frontend/src/assets/brazil.json').write_text(json.dumps(output,separators=(',',':')), encoding='utf-8')
