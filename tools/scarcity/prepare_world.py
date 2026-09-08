#!/usr/bin/env python3
"""One-time preparation of an unopened WorldPainter 26.x export, never a live world.
Requires nbtlib==2.0.4 and numpy. Writes only a new output directory, plus audit.json.
"""
import argparse
import fcntl
import hashlib
import heapq
import io
import json
import math
from pathlib import Path
import shutil
import struct
import zlib
import nbtlib as nbt
import numpy as np

ALGORITHM = 'diamond-deposits-v1'
ORES = {'minecraft:diamond_ore', 'minecraft:deepslate_diamond_ore'}
HOSTS = {'minecraft:stone', 'minecraft:deepslate'}


def contains(box, x, y, z):
    return box[0] <= x <= box[3] and box[1] <= y <= box[4] and box[2] <= z <= box[5]


def spec_read(path):
    spec = json.loads(path.read_text())
    if set(spec) - {'format', 'seed', 'bounds', 'deposits', 'container-policy'} or not {'format', 'seed', 'bounds', 'deposits'} <= set(spec) or spec['format'] != 1:
        raise ValueError('spec: expected format, seed, bounds, deposits; format=1')
    if spec.get('container-policy', 'reject') not in ('reject', 'strip-selected'): raise ValueError('container-policy: expected reject or strip-selected')
    if type(spec['seed']) is not int: raise ValueError('seed: expected integer')
    def box(b):
        if len(b) != 6 or any(type(v) is not int for v in b): raise ValueError('bounds: six integers required')
        if any(b[i] > b[i+3] for i in range(3)): raise ValueError('bounds: inverted')
    b = spec['bounds']; box(b)
    if b[0] % 16 or b[2] % 16 or (b[3]+1) % 16 or (b[5]+1) % 16:
        raise ValueError('bounds: whole chunks required')
    if not (-64 == b[1] and b[4] == 319): raise ValueError('bounds: this WorldPainter tool requires full -64..319 height')
    if b[3]-b[0]+1 > 2048 or b[5]-b[2]+1 > 2048: raise ValueError('bounds: maximum 2048-square preparation')
    if not 1 <= len(spec['deposits']) <= 64: raise ValueError('deposits: expected 1..64')
    ids = set()
    for d in spec['deposits']:
        if set(d) != {'id', 'bounds', 'ore-count'}: raise ValueError('deposit: unknown or missing keys')
        if not isinstance(d['id'], str) or not d['id'] or d['id'] in ids: raise ValueError('deposit.id: invalid/duplicate')
        ids.add(d['id']); box(d['bounds'])
        q = d['bounds']
        if not contains(b, *q[:3]) or not contains(b, *q[3:]): raise ValueError('deposit: outside world bounds')
        if type(d['ore-count']) is not int or not 1 <= d['ore-count'] <= 65536: raise ValueError('ore-count: expected 1..65536')
    for i, a in enumerate(spec['deposits']):
        for c in spec['deposits'][i+1:]:
            if all(a['bounds'][k] <= c['bounds'][k+3] and c['bounds'][k] <= a['bounds'][k+3] for k in range(3)):
                raise ValueError('deposits: overlapping boxes')
    return spec


def region_chunks(path):
    data = path.read_bytes()
    if len(data) < 8192: raise ValueError(f'{path}: truncated header')
    rx, rz = map(int, path.stem.split('.')[1:])
    for slot in range(1024):
        loc = data[slot*4:slot*4+4]; offset = int.from_bytes(loc[:3], 'big')*4096
        if not offset: continue
        length = int.from_bytes(data[offset:offset+4], 'big')
        if offset < 8192 or length < 1 or length+4 > loc[3]*4096 or offset+length+4 > len(data):
            raise ValueError(f'{path}: invalid allocation at {slot}')
        if data[offset+4] != 2: raise ValueError(f'{path}: only inline zlib chunks supported')
        doc = nbt.File.parse(io.BytesIO(zlib.decompress(data[offset+5:offset+4+length])))
        if int(doc['xPos']) != rx*32+slot%32 or int(doc['zPos']) != rz*32+slot//32:
            raise ValueError(f'{path}: chunk coordinate mismatch')
        yield slot, doc


def indices(states):
    size = len(states['palette'])
    if size == 1: return np.zeros(4096, dtype=np.uint64)
    bits = max(4, (size-1).bit_length()); per = 64//bits
    words = np.asarray(states['data'], dtype=np.int64).view(np.uint64)
    if len(words) != math.ceil(4096/per): raise ValueError('invalid packed block data')
    pos = np.arange(4096, dtype=np.uint64)
    values = (words[pos//per] >> ((pos%per)*bits)) & ((1<<bits)-1)
    if np.any(values >= size): raise ValueError('invalid palette index')
    return values


def set_indices(states, values):
    bits = max(4, (len(states['palette'])-1).bit_length()); per = 64//bits
    words = np.zeros(math.ceil(4096/per), dtype=np.uint64)
    for start in range(per):
        part = values[start::per].astype(np.uint64)
        words[:len(part)] |= part << (start*bits)
    states['data'] = nbt.LongArray(words.view(np.int64))


def write_region(path, chunks):
    # Compact allocations; timestamps zero make preparation output deterministic.
    header = bytearray(8192); body = bytearray(); sector = 2
    for slot, doc in chunks:
        stream = io.BytesIO(); doc.write(stream)
        payload = b'\x02' + zlib.compress(stream.getvalue())
        count = math.ceil((4+len(payload))/4096)
        if count > 255: raise ValueError('chunk too large for inline region')
        header[slot*4:slot*4+4] = sector.to_bytes(3, 'big') + bytes([count])
        body += struct.pack('>I', len(payload)) + payload
        body += bytes(count*4096-4-len(payload)); sector += count
    path.write_bytes(header+body)


def digest_files(root):
    files = {}
    for p in sorted(root.rglob('*')):
        if p.is_file(): files[str(p.relative_to(root))] = hashlib.sha256(p.read_bytes()).hexdigest()
    digest = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return {'sha256': digest, 'files': files}


def selected_item(node):
    if not isinstance(node, nbt.Compound): return False
    name=str(node.get('id', ''))
    return name == 'minecraft:diamond' or name.startswith('minecraft:diamond_') or name == 'minecraft:deepslate_diamond_ore'


def strip_selected(node):
    if selected_item(node): return False
    if isinstance(node, nbt.Compound):
        for key in list(node):
            if not strip_selected(node[key]): del node[key]
    elif isinstance(node, nbt.List):
        for index in range(len(node)-1, -1, -1):
            if not strip_selected(node[index]): del node[index]
    return True


def introductions(node, tally):
    if isinstance(node, nbt.Compound):
        for key, value in node.items():
            if key.lower() in ('loottable', 'loot_table'): tally['lazy-loot-tables'] += 1
            if key == 'id' and selected_item(node):
                tally['selected-item-stacks'] += 1
            if key.lower() == 'offers': tally['merchant-offer-compounds'] += 1
            introductions(value, tally)
    elif isinstance(node, nbt.List):
        for item in node: introductions(item, tally)


def process(source, spec, output=None):
    # WorldPainter writes an 8-byte timestamp lock. Hold a shared OS lock so a Java
    # server cannot open this source while it is scanned. Paper's own lock is rejected.
    lock = source/'session.lock'
    if lock.exists():
        if lock.stat().st_size != 8: raise ValueError('source: not a WorldPainter timestamp lock')
        with lock.open('rb') as handle:
            fcntl.lockf(handle, fcntl.LOCK_SH | fcntl.LOCK_NB)
            return process_locked(source, spec, output)
    return process_locked(source, spec, output)


def process_locked(source, spec, output=None):
    if not source.is_dir(): raise ValueError('source: world directory missing')
    if (source/'PREPARATION-INCOMPLETE').exists(): raise ValueError('source: incomplete preparation')
    if output and output.exists(): raise ValueError('output: must not exist; never prepare a world twice')
    if any(source.rglob('playerdata')):
        raise ValueError('source: only unopened exports without playerdata are accepted')
    region = source/'dimensions/minecraft/overworld/region'
    paths = sorted(region.glob('r.*.*.mca'))
    if not paths: raise ValueError('source: expected 26.x overworld region directory')
    if set(source.rglob('*.mca')) != set(paths):
        raise ValueError('source: additional dimensions/entity/POI regions need a separate audit; not supported by this export tool')
    original = digest_files(source)
    b = spec['bounds']; expected = {(x,z) for x in range(b[0]//16,(b[3]+1)//16) for z in range(b[2]//16,(b[5]+1)//16)}
    seen = set(); heaps = {d['id']: [] for d in spec['deposits']}
    tally = {'ore-blocks': 0, 'ore-outside-deposits': 0, 'lazy-loot-tables': 0, 'selected-item-stacks': 0, 'merchant-offer-compounds': 0}
    zone_counts = {d['id']: 0 for d in spec['deposits']}
    for path in paths:
        for slot, doc in region_chunks(path):
            cx, cz = int(doc['xPos']), int(doc['zPos']); coord=(cx,cz)
            if coord in seen or coord not in expected: raise ValueError('chunks: duplicate or outside declared bounds')
            if str(doc['Status']) not in ('full', 'minecraft:full') or int(doc.get('InhabitedTime', 0)) != 0:
                raise ValueError('source: requires full uninhabited chunks')
            seen.add(coord)
            introductions(doc.get('block_entities', []), tally)
            introductions(doc.get('entities', []), tally)
            for sec in doc['sections']:
                sy=int(sec['Y'])*16; states=sec.get('block_states')
                if states is None: continue
                names=[str(p['Name']) for p in states['palette']]
                ores=[i for i,n in enumerate(names) if n in ORES]
                nearby=[d for d in spec['deposits'] if cx*16 <= d['bounds'][3] and cx*16+15 >= d['bounds'][0] and cz*16 <= d['bounds'][5] and cz*16+15 >= d['bounds'][2] and sy <= d['bounds'][4] and sy+15 >= d['bounds'][1]]
                if not ores and not (output and nearby): continue
                values=indices(states)
                for i in np.flatnonzero(np.isin(values, ores)):
                    x,y,z=cx*16+int(i)%16,sy+int(i)//256,cz*16+(int(i)//16)%16
                    tally['ore-blocks'] += 1
                    matches=[d for d in nearby if contains(d['bounds'],x,y,z)]
                    if not matches: tally['ore-outside-deposits'] += 1
                    for d in matches: zone_counts[d['id']] += 1
                if output:
                    host_ids=[i for i,n in enumerate(names) if n in HOSTS or n in ORES]
                    for i in np.flatnonzero(np.isin(values, host_ids)) if nearby else []:
                        x,y,z=cx*16+int(i)%16,sy+int(i)//256,cz*16+(int(i)//16)%16
                        for d in nearby:
                            if not contains(d['bounds'],x,y,z): continue
                            rank=int.from_bytes(hashlib.sha256(f"{ALGORITHM}:{spec['seed']}:{d['id']}:{x}:{y}:{z}".encode()).digest(),'big')
                            entry=(-rank,x,y,z); heap=heaps[d['id']]
                            if len(heap) < d['ore-count']: heapq.heappush(heap,entry)
                            elif entry > heap[0]: heapq.heapreplace(heap,entry)
        print(f'scanned {path.name}: {len(seen)}/{len(expected)} chunks', flush=True)
    if seen != expected: raise ValueError(f'chunks: missing {len(expected-seen)}; no generation is permitted')
    report={'algorithm':ALGORITHM,'spec':spec,'source':original,'chunks':len(seen),'observed':tally,'deposits':zone_counts}
    if output:
        if tally['lazy-loot-tables'] or tally['merchant-offer-compounds'] or (tally['selected-item-stacks'] and spec.get('container-policy', 'reject') == 'reject'):
            raise ValueError('source: alternate supply found; resolve explicitly before preparation')
        selected={}
        for d in spec['deposits']:
            heap=heaps[d['id']]
            if len(heap) != d['ore-count']: raise ValueError(f"deposit {d['id']}: insufficient stone/deepslate")
            for _,x,y,z in heap: selected.setdefault((x//16,z//16),set()).add((x,y,z))
        if output.exists(): raise ValueError('output: must not exist; never prepare a world twice')
        if source == output or source in output.parents: raise ValueError('output: must be outside source')
        shutil.copytree(source,output)
        # A failure leaves an unmistakably incomplete staging directory. Never auto-resume it.
        marker=output/'PREPARATION-INCOMPLETE'; marker.write_text(ALGORITHM)
        for path in paths:
            chunks=list(region_chunks(path))
            for slot,doc in chunks:
                strip_selected(doc.get('block_entities', []))
                strip_selected(doc.get('entities', []))
                cx,cz=int(doc['xPos']),int(doc['zPos']); chosen=selected.get((cx,cz),set())
                for sec in doc['sections']:
                    states=sec.get('block_states')
                    if states is None: continue
                    sy=int(sec['Y'])*16
                    for p in states['palette']:
                        if str(p['Name']) in ORES:
                            p['Name']=nbt.String('minecraft:deepslate' if str(p['Name']).startswith('minecraft:deepslate') else 'minecraft:stone')
                    points=[(x,y,z) for x,y,z in chosen if sy <= y < sy+16]
                    if not points: continue
                    values=indices(states)
                    for x,y,z in points:
                        idx=(y-sy)*256+(z%16)*16+x%16
                        host=str(states['palette'][int(values[idx])]['Name'])
                        ore='minecraft:deepslate_diamond_ore' if host=='minecraft:deepslate' else 'minecraft:diamond_ore'
                        names=[str(p['Name']) for p in states['palette']]
                        if ore not in names: states['palette'].append(nbt.Compound({'Name':nbt.String(ore)})); names.append(ore)
                        values[idx]=names.index(ore)
                    set_indices(states,values)
            write_region(output/path.relative_to(source),chunks)
        if digest_files(source) != original: raise ValueError('source changed during preparation; discard output')
        marker.unlink()
        report['prepared']=digest_files(output)
        report['removed-original-ore']=tally['ore-blocks']
        report['placed-ore']=sum(d['ore-count'] for d in spec['deposits'])
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path);parser.add_argument('spec',type=Path)
    parser.add_argument('--output',type=Path);parser.add_argument('--unopened-export',action='store_true')
    parser.add_argument('--report',required=True,type=Path)
    args=parser.parse_args()
    source=args.source.resolve();output=args.output.resolve() if args.output else None
    if output and not args.unopened_export: parser.error('preparation requires --unopened-export attestation')
    if args.report.exists(): parser.error('report must not exist')
    if source in args.report.resolve().parents or (output and output in args.report.resolve().parents): parser.error('report must be outside the world')
    report=process(source,spec_read(args.spec),output)
    args.report.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
    print('Report SHA256:',hashlib.sha256(args.report.read_bytes()).hexdigest())

if __name__=='__main__': main()
