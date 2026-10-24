"""Zero-cost local TripoSR trial. Outputs an AI approximation, never auto-publishes it.

Run with .runtime/3d-venv/Scripts/python.exe; see assets/source/3d/LOCAL-GENERATION.md.
The CPU marching-cubes adapter avoids a custom Windows CUDA extension.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import types

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--product-id', required=True)
    parser.add_argument('--colorway-id', required=True)
    parser.add_argument('--resolution', type=int, default=192, choices=[128, 192, 256])
    parser.add_argument('--device', choices=['cuda', 'cpu'], default='cuda')
    parser.add_argument('--background', choices=['white', 'u2netp'], default='white')
    parser.add_argument('--output', type=Path, default=ROOT / '.local/work/3d-preview')
    args = parser.parse_args()
    os.environ['U2NET_HOME'] = str(ROOT / '.runtime/rembg')
    sys.path.insert(0, str(ROOT / '.runtime/TripoSR'))
    import numpy as np
    import torch
    from PIL import Image, ImageFilter
    import rembg
    from skimage.measure import marching_cubes
    from transformers import ViTConfig, ViTModel

    if args.device == 'cuda' and not torch.cuda.is_available():
        raise RuntimeError('CUDA GPU required for this trial; no silent CPU fallback.')
    if args.device == 'cpu':
        torch.set_num_threads(min(8, os.cpu_count() or 1))
    weights = ROOT / '.runtime/triposr-weights'
    expected = '429e2c6b22a0923967459de24d67f05962b235f79cde6b032aa7ed2ffcd970ee'
    with (weights / 'model.ckpt').open('rb') as checkpoint:
        if hashlib.file_digest(checkpoint, 'sha256').hexdigest() != expected:
            raise ValueError('Model checkpoint SHA256 does not match the recorded source.')

    # Upstream swaps z/x after torchmcubes. Return that convention from skimage,
    # which reads array axes directly as x/y/z, so the final geometry stays correct.
    adapter = types.ModuleType('torchmcubes')
    def extract(level, threshold):
        vertices, faces, _, _ = marching_cubes(level.detach().cpu().numpy(), threshold)
        return torch.from_numpy(vertices[:, [2, 1, 0]].copy()), torch.from_numpy(faces.copy().astype(np.int64))
    adapter.marching_cubes = extract
    sys.modules['torchmcubes'] = adapter
    from tsr.models.tokenizers.image import DINOSingleImageTokenizer
    def configure_dino(self):
        # DINO ViT-B/16 architecture; its learned weights are in the TripoSR ckpt.
        self.model = ViTModel(ViTConfig(image_size=224, patch_size=16, hidden_size=768,
            intermediate_size=3072, num_hidden_layers=12, num_attention_heads=12,
            qkv_bias=True, layer_norm_eps=1e-12, hidden_dropout_prob=0., attention_probs_dropout_prob=0.))
        for name, values in [('image_mean', [.485, .456, .406]), ('image_std', [.229, .224, .225])]:
            self.register_buffer(name, torch.as_tensor(values).reshape(1, 1, 3, 1, 1), persistent=False)
    DINOSingleImageTokenizer.configure = configure_dino
    from tsr.system import TSR
    from tsr.utils import resize_foreground

    output = args.output / args.product_id / args.colorway_id
    output.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    print('Preprocessing', args.image.name, flush=True)
    image = Image.open(args.image).convert('RGB')
    if args.background == 'u2netp':
        rgba = rembg.remove(image, session=rembg.new_session('u2netp'))
    else:
        # Supplied catalog photos have a plain white background. Keep the largest
        # connected silhouette and fill its white interior, instead of downloading
        # a second network merely to segment this controlled input.
        from scipy import ndimage
        foreground = np.min(np.asarray(image), axis=2) < 242
        foreground = ndimage.binary_closing(foreground, iterations=2)
        labels, count = ndimage.label(foreground)
        if not count:
            raise ValueError('No foreground found in white-background photo.')
        sizes = np.bincount(labels.ravel()); sizes[0] = 0
        foreground = ndimage.binary_fill_holes(labels == sizes.argmax())
        if not .02 < foreground.mean() < .9:
            raise ValueError('Foreground coverage is implausible; inspect the source photo.')
        alpha = Image.fromarray(foreground.astype(np.uint8) * 255).filter(ImageFilter.GaussianBlur(.5))
        rgba = image.convert('RGBA'); rgba.putalpha(alpha)
    rgba.save(output / 'cutout.png')
    pixels = np.asarray(resize_foreground(rgba, .85)).astype(np.float32) / 255
    pixels = pixels[:, :, :3] * pixels[:, :, 3:4] + (1 - pixels[:, :, 3:4]) * .5
    model = TSR.from_pretrained(str(weights), config_name='config.yaml', weight_name='model.ckpt')
    model.renderer.set_chunk_size(4096)
    model.to(args.device).eval()
    if args.device == 'cuda':
        torch.cuda.reset_peak_memory_stats()
    print('Reconstructing on', torch.cuda.get_device_name() if args.device == 'cuda' else 'CPU', flush=True)
    with torch.inference_mode():
        codes = model([Image.fromarray((pixels * 255).astype(np.uint8))], device=args.device)
        mesh = model.extract_mesh(codes, has_vertex_color=True, resolution=args.resolution)[0]
    # Keep the largest connected component; detached background fragments are not shoe parts.
    components = mesh.split(only_watertight=False)
    if len(components):
        mesh = max(components, key=lambda part: part.area)
    if len(mesh.faces) > 200000:
        raise ValueError('Trial exceeds mobile triangle budget; inspect and simplify before use.')
    # TripoSR uses Z-up with the reference facing +X; glTF viewers use Y-up.
    mesh.apply_transform(np.array([[0, 1, 0, 0], [0, 0, 1, 0], [1, 0, 0, 0], [0, 0, 0, 1]], dtype=float))
    path = output / 'preview.glb'
    mesh.export(path)
    report = {'productId': args.product_id, 'colorwayId': args.colorway_id,
        'origin': 'AI_PREVIEW', 'reviewed': False, 'file': str(path),
        'inputSha256': hashlib.sha256(args.image.read_bytes()).hexdigest(),
        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'bytes': path.stat().st_size,
        'triangles': len(mesh.faces), 'seconds': round(time.monotonic() - started, 2),
        'device': args.device,
        'peakCudaMiB': round(torch.cuda.max_memory_allocated() / 1024**2) if args.device == 'cuda' else 0,
        'generator': 'TripoSR + CPU skimage marching cubes', 'resolution': args.resolution,
        'backgroundMethod': args.background,
        'coordinates': 'TripoSR Z-up to glTF Y-up, reference side +Z',
        'weightsSha256': expected,
        'notice': 'AI approximation from one photo. Hidden surfaces, logos and dimensions are unverified. Not AR try-on.'}
    (output / 'generation.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
