"""Local silhouette/projection experiment, not calibrated 3D reconstruction.

Consumes four manually reviewed views of one ASICS colorway. No AI weights,
external generation API, inferred camera calibration or catalog publishing.
"""
import argparse
import hashlib
import json
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageOps
from scipy import ndimage
from skimage.measure import marching_cubes
import trimesh

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / '.local/work/3d-preview/source-views/ASICS-KAYANO14-1203A537-301'
OUTPUT = ROOT / '.local/work/3d-preview/multiview-hull/ASICS-KAYANO14-1203A537-301'


def photo_mask(path, transform):
    image = Image.open(path).convert('RGB')
    if transform == 'mirror':
        image = ImageOps.mirror(image)
    elif transform == 'rotate180':
        image = image.transpose(Image.Transpose.ROTATE_180)
    pixels = np.asarray(image)
    mask = np.min(pixels, axis=2) < 225
    mask = ndimage.binary_closing(mask, iterations=2)
    labels, count = ndimage.label(mask)
    if not count:
        raise ValueError(f'No shoe silhouette: {path}')
    counts = np.bincount(labels.ravel()); counts[0] = 0
    mask = ndimage.binary_fill_holes(labels == counts.argmax())
    ys, xs = np.nonzero(mask)
    if not .03 < mask.mean() < .8:
        raise ValueError('Implausible foreground; review the source.')
    # Identical crop for image and silhouette preserves photo/mesh registration.
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    crop = image.crop(box)
    mask = mask[box[1]:box[3], box[0]:box[2]]
    return crop, mask, box


def sample_mask(mask, u, v):
    return ndimage.map_coordinates(mask.astype(np.float32),
        [v * (mask.shape[0] - 1), u * (mask.shape[1] - 1)],
        order=1, mode='constant', cval=0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--resolution', type=int, default=192, choices=[128, 192, 256])
    args = parser.parse_args()
    started = time.monotonic()
    args.output.mkdir(parents=True, exist_ok=True)
    source_manifest = json.loads((args.source / 'source-manifest.json').read_text(encoding='utf-8'))
    # All four are single-shoe photos. Pair photos 02/04 would create two shoes.
    # Toe is aligned to -X. Camera metrics remain unknown, explicitly recorded.
    assignments = [('outer', 1, 'none'), ('inner', 3, 'mirror'),
                   ('upper', 5, 'rotate180'), ('sole', 6, 'none')]
    photos, masks, provenance = {}, {}, []
    contact = Image.new('RGB', (1200, 660), '#eeeeeb')
    draw = ImageDraw.Draw(contact)
    for index, (name, number, transform) in enumerate(assignments):
        path = args.source / f'view-{number:02d}.jpg'
        photos[name], masks[name], box = photo_mask(path, transform)
        rgba = photos[name].convert('RGBA')
        rgba.putalpha(Image.fromarray(masks[name].astype(np.uint8) * 255))
        rgba.save(args.output / f'{name}-cutout.png')
        thumb = ImageOps.contain(photos[name], (580, 280))
        x, y = index % 2 * 600, index // 2 * 330
        contact.paste(thumb, (x + (600-thumb.width)//2, y + (280-thumb.height)//2))
        draw.text((x+16, y+300), f'{name.upper()} | source {number:02d} | {transform}', fill='#222222')
        source = next(item for item in source_manifest['views'] if item['position'] == number)
        provenance.append({'role':name,'sourceView':number,'transform':transform,'crop':box,
            'inputSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'source':source['source']})
    contact.save(args.output / 'inputs.jpg', quality=94)

    # Orthographic approximation. Ratios come from the side and sole crops;
    # catalog photos are not calibrated and these are NOT real-world dimensions.
    height = float(np.mean([masks[k].shape[0] / masks[k].shape[1] for k in ['outer','inner']]))
    width = masks['sole'].shape[0] / masks['sole'].shape[1]
    nx = args.resolution
    ny, nz = max(48, round(nx * height)), max(48, round(nx * width))
    x = np.linspace(-.53, .53, nx, dtype=np.float32)
    y = np.linspace(-height*.55, height*.55, ny, dtype=np.float32)
    z = np.linspace(-width*.55, width*.55, nz, dtype=np.float32)
    xx, yy, zz = np.meshgrid(x, y, z, indexing='ij')
    u = xx + .5
    sy = .5 - yy / height
    tz = .5 + zz / width
    occupancy = np.minimum(sample_mask(masks['outer'], u, sy), sample_mask(masks['inner'], u, sy))
    occupancy = np.minimum(occupancy, sample_mask(masks['upper'], u, tz))
    occupancy = np.minimum(occupancy, sample_mask(masks['sole'], u, 1-tz))
    occupancy = ndimage.gaussian_filter(occupancy, sigma=.85)
    vertices, faces, _, _ = marching_cubes(occupancy, .4,
        spacing=(float(x[1]-x[0]), float(y[1]-y[0]), float(z[1]-z[0])))
    vertices += np.array([x[0], y[0], z[0]])
    mesh = trimesh.Trimesh(vertices=vertices, faces=faces, process=True)
    mesh.fix_normals()
    if len(mesh.faces) > 200000:
        raise ValueError('Exceeds mobile trial triangle limit.')
    geometry_path = args.output / 'geometry.glb'
    mesh.export(geometry_path)

    # Four actual photos in a padded atlas. Face-wise orthographic UVs keep
    # lettering sharp; discontinuities and foreshortening must be reviewed.
    tile, padding = 1024, 12
    atlas = Image.new('RGB', (tile*2, tile*2), 'white')
    names = ['outer','inner','upper','sole']
    for index, name in enumerate(names):
        atlas.paste(photos[name].resize((tile-2*padding, tile-2*padding), Image.Resampling.LANCZOS),
            ((index % 2)*tile+padding, (index // 2)*tile+padding))
    atlas.save(args.output / 'projection-atlas.jpg', quality=95)
    normals = mesh.face_normals
    # Y-facing surfaces use upper/sole photos; other faces use the nearest side.
    vertical = np.abs(normals[:,1]) > np.abs(normals[:,2]) * 1.15
    charts = np.where(vertical, np.where(normals[:,1] > 0, 2, 3),
                      np.where(normals[:,2] > 0, 0, 1))
    flat_vertices = mesh.vertices[mesh.faces].reshape(-1, 3)
    per_vertex_charts = np.repeat(charts, 3)
    uu = np.clip(flat_vertices[:,0] + .5, 0, 1)
    vv = np.clip(.5 - flat_vertices[:,1] / height, 0, 1)
    upper = per_vertex_charts == 2
    sole = per_vertex_charts == 3
    vv[upper] = np.clip(.5 + flat_vertices[upper,2] / width, 0, 1)
    vv[sole] = np.clip(.5 - flat_vertices[sole,2] / width, 0, 1)
    uv = np.column_stack(((per_vertex_charts % 2 * tile + padding + uu*(tile-2*padding)) / (tile*2),
        1 - (per_vertex_charts // 2 * tile + padding + vv*(tile-2*padding)) / (tile*2)))
    material = trimesh.visual.material.PBRMaterial(name='Actual photo projections',
        baseColorTexture=atlas, metallicFactor=0., roughnessFactor=1., doubleSided=False)
    textured = trimesh.Trimesh(vertices=flat_vertices,
        faces=np.arange(len(flat_vertices)).reshape(-1,3),
        vertex_normals=mesh.vertex_normals[mesh.faces].reshape(-1,3), process=False)
    textured.visual = trimesh.visual.TextureVisuals(uv=uv, material=material)
    textured.merge_vertices(merge_tex=False, merge_norm=False)
    path = args.output / 'preview.glb'
    textured.export(path)
    result = {'productId':'SPU-234E824E28E2','colorwayId':'CLR-774E8ADF3AC0',
        'origin':'PHOTO_SILHOUETTE_EXPERIMENT','reviewed':False,'publishable':False,
        'generator':'Four-view silhouette intersection + original-photo projection',
        'inputViews':provenance, 'excludedViews':{'2':'pair of shoes','4':'pair of shoes'},
        'cameraCalibration':'UNKNOWN; orthographic approximation, manually aligned',
        'physicalDimensions':'UNKNOWN; normalized, not valid for fit or size',
        'limitations':['No recovered interior, laces or fine geometry',
            'Photos have perspective, pose and left/right-shoe differences',
            'Projection seams, occlusion and depth remain approximate',
            'No AR try-on, no approved product model, no automatic catalog binding'],
        'bytes':path.stat().st_size,'triangles':len(mesh.faces),
        'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'geometrySha256':hashlib.sha256(geometry_path.read_bytes()).hexdigest(),
        'seconds':round(time.monotonic()-started,2),'device':'cpu','resolution':args.resolution,
        'reviewStatus':'PENDING_VISUAL_REVIEW'}
    (args.output / 'generation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
