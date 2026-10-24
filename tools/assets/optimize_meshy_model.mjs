// Local-only derivative. Keep the original and texture bytes unchanged.
// Runtime setup: npm install --prefix .runtime/gltf-tools --no-audit --no-fund
//   @gltf-transform/core@4.5.1 @gltf-transform/functions@4.5.1 meshoptimizer@1.3.0
import { NodeIO } from '../../.runtime/gltf-tools/node_modules/@gltf-transform/core/dist/index.js';
import { dedup, weld, simplify, prune } from '../../.runtime/gltf-tools/node_modules/@gltf-transform/functions/dist/index.js';
import { MeshoptSimplifier } from '../../.runtime/gltf-tools/node_modules/meshoptimizer/index.js';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, access } from 'node:fs/promises';
import path from 'node:path';

const [input, output] = process.argv.slice(2);
if (!input || !output) throw Error('Usage: node tools/assets/optimize_meshy_model.mjs input.glb output-directory');
const directory = path.resolve(output);
if (path.resolve(input).startsWith(directory + path.sep)) throw Error('Keep source outside output directory.');
await mkdir(directory, { recursive: true });
const original = path.join(directory, 'original.glb');
try { await access(original); throw Error('Original already exists; use a new directory.'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const hash = data => createHash('sha256').update(data).digest('hex');
const source = await readFile(input);
const io = new NodeIO();
const document = await io.readBinary(source);
const triangles = doc => doc.getRoot().listMeshes().reduce((sum, mesh) => sum + mesh.listPrimitives().reduce((n, primitive) => {
  if (primitive.getMode() !== 4 || !primitive.getIndices()) throw Error('Expected indexed triangles.');
  return n + primitive.getIndices().getCount() / 3;
}, 0), 0);
const before = triangles(document);
const textures = document.getRoot().listTextures().map(texture => ({
  mimeType: texture.getMimeType(), size: texture.getSize(), sha256: hash(texture.getImage()),
}));
await MeshoptSimplifier.ready;
await document.transform(dedup(), weld(), simplify({ simplifier: MeshoptSimplifier, ratio: .1, error: .001, lockBorder: true }), prune());
const after = triangles(document);
const preview = await io.writeBinary(document);
if (after > 200000 || preview.byteLength > 25 * 1024 * 1024) throw Error('Derivative exceeds current mobile review limits.');
const texturesAfter = document.getRoot().listTextures().map(texture => hash(texture.getImage()));
if (texturesAfter.length !== textures.length || textures.some((texture, index) => texture.sha256 !== texturesAfter[index])) throw Error('Texture changed unexpectedly.');
await writeFile(original, source, { flag: 'wx' });
await writeFile(path.join(directory, 'mobile.glb'), preview, { flag: 'wx' });
const report = {
  provider: 'Meshy', acquiredBy: 'user supplied download',
  original: { file: 'original.glb', sha256: hash(source), bytes: source.length, triangles: before },
  mobile: { file: 'mobile.glb', sha256: hash(preview), bytes: preview.byteLength, triangles: after },
  textures, textureBytesUnchanged: true,
  optimization: { tool: 'glTF Transform 4.5.1 / meshoptimizer 1.3.0', ratio: .1, error: .001, lockBorder: true, compression: 'none' },
  candidateProduct: { id: 'wp-kobe', color: '道奇蓝', match: 'user screenshot; physical accuracy unverified' },
  reviewed: false, publishable: false, licenseStatus: 'User account plan and output rights not yet verified',
  limitations: ['Single photo generation; unseen surfaces inferred', 'Scale is not a real shoe measurement', 'No AR foot tracking or fit estimation'],
};
await writeFile(path.join(directory, 'review.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
