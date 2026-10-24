"""Read-only audit of downloaded shoe photos vs original source gallery metadata.

Extra gallery records are candidates, not verified same-color images or model
inputs. This script neither downloads media nor mutates the source dataset.
"""
import argparse
import json
from collections import Counter
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dataset', type=Path)
    parser.add_argument('--output', type=Path,
        default=ROOT / '.local/work/3d-preview/source-view-audit.json')
    args = parser.parse_args()
    requirements = json.loads((ROOT / '.local/work/catalog-import/shoe-model-requirements.json').read_text(encoding='utf-8-sig'))
    snapshots = json.loads((args.dataset / '数据/来源商品快照.json').read_text(encoding='utf-8-sig'))
    image_rows = json.loads((args.dataset / '数据/images.json').read_text(encoding='utf-8-sig'))
    lookup = {(entry['source'], entry['product']['handle']):entry['product'] for entry in snapshots}
    rows = []
    for product in requirements:
        for color in product['colorways']:
            url = urlparse(color['sourceUrl'])
            source_product = lookup.get((url.netloc, url.path.rstrip('/').split('/')[-1]))
            downloaded = [row for row in image_rows if row['spu_id'] == product['productId']
                and row['colorway_id'] == color['colorwayId'] and row['status'] == 'ok'
                and (args.dataset / row['local_file']).is_file()]
            known_urls = {row['source_url'] for row in downloaded}
            gallery = source_product.get('images', []) if source_product else []
            rows.append({'productId':product['productId'],'colorwayId':color['colorwayId'],
                'product':product['name'],'colorway':color['name'],'sourceUrl':color['sourceUrl'],
                'downloadedImages':len(downloaded),'sourceProductFound':source_product is not None,
                'sourceGalleryImages':len(gallery),
                'extraCandidates':[{'sourceUrl':image['src'],'sourceImageId':str(image['id']),
                    'position':image.get('position'),'width':image.get('width'),'height':image.get('height'),
                    'status':'UNREVIEWED_SOURCE_GALLERY_CANDIDATE'}
                    for image in gallery if image['src'] not in known_urls]})
    summary = {'shoeProducts':len(requirements),'shoeColorways':len(rows),
        'downloadedCountDistribution':dict(sorted(Counter(row['downloadedImages'] for row in rows).items())),
        'matchedSourceProducts':sum(row['sourceProductFound'] for row in rows),
        'colorwaysWithExtraCandidates':sum(bool(row['extraCandidates']) for row in rows),
        'notice':'Metadata only. Extra source photos need color/pose/angle review. Not all gallery images are reconstruction inputs.',
        'rows':rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({key:value for key,value in summary.items() if key != 'rows'},ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
