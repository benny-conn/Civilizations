"""Read-only S5 stopped-server inventory audit; includes generated chunks outside borders."""
from pathlib import Path
import sys,io,zlib,json,hashlib,sqlite3,collections,fcntl,argparse,uuid
sys.path.insert(0,'tools/scarcity')
from prepare_world import region_chunks,indices,ORES,contains
import numpy as np
import nbtlib as nbt
parser=argparse.ArgumentParser();parser.add_argument('--expected-mined',type=int,default=0);parser.add_argument('--report',default=None);args=parser.parse_args();assert args.expected_mined in (0,1)
root=Path('server');assert not root.is_symlink() and (root/'S5-FIXTURE').exists();lock=(root/'world/session.lock').open('rb');fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB);report={};spec=json.loads((root/'verification/prototype-deposits.json').read_text())
def scan_tags(node,result):
 if isinstance(node,nbt.Compound):
  name=str(node.get('id',''))
  if name.startswith('minecraft:') and ('count' in node or 'Count' in node or name in {'minecraft:diamond','minecraft:diamond_block','minecraft:deepslate_diamond_ore'}):
   result['items'][name]+=int(node.get('count',node.get('Count',1)))
  if name=='minecraft:cow':
   result['cow-entities']+=1
   logical=str(node.get('BukkitValues',{}).get('civilizations:mob-id','UNMANAGED'))
   raw=node.get('UUID',[])
   entity=str(uuid.UUID(int=sum((int(v)&0xffffffff) << ((3-i)*32) for i,v in enumerate(raw)))) if len(raw)==4 else 'MISSING'
   result['cow-bindings'].setdefault(logical,[]).append(entity)
  for key,value in node.items():
   if key.lower() in ('loottable','loot_table'):result['lazy-loot-tables']+=1
   if key.lower()=='offers':result['merchant-offer-compounds']+=1
   scan_tags(value,result)
 elif isinstance(node,nbt.List):
  for child in node:scan_tags(child,result)
def entity_chunks(path):
 data=path.read_bytes()
 for slot in range(1024):
  off=int.from_bytes(data[slot*4:slot*4+3],'big')*4096
  if off:
   length=int.from_bytes(data[off:off+4],'big');assert data[off+4]==2
   yield nbt.File.parse(io.BytesIO(zlib.decompress(data[off+5:off+4+length])))
for dimension in sorted((root/'world/dimensions/minecraft').iterdir()):
 if not dimension.is_dir():continue
 name=dimension.name
 if name not in ('overworld','the_nether'):raise ValueError('Unexpected dimension '+name)
 results={'chunks':0,'full-chunks':0,'ore-blocks':0,'ore-outside-deposits':0,'cane-blocks':0,'cow-entities':0,'cow-bindings':{},'lazy-loot-tables':0,'merchant-offer-compounds':0,'items':collections.Counter(),'deposits':collections.Counter()}
 full=set()
 for path in sorted((dimension/'region').glob('*.mca')):
  for slot,doc in region_chunks(path):
   results['chunks']+=1;cx,cz=int(doc['xPos']),int(doc['zPos'])
   if str(doc['Status']) in ('full','minecraft:full'):full.add((cx,cz))
   scan_tags(doc.get('block_entities',[]),results)
   for sec in doc.get('sections',[]):
    states=sec.get('block_states')
    if states is None:continue
    palette=[str(p['Name']) for p in states['palette']];selected=[i for i,n in enumerate(palette) if n in ORES or n=='minecraft:sugar_cane']
    if not selected:continue
    values=indices(states)
    for index in np.flatnonzero(np.isin(values,selected)):
     material=palette[int(values[index])]
     if material=='minecraft:sugar_cane':results['cane-blocks']+=1;continue
     results['ore-blocks']+=1
     x=cx*16+int(index)%16;y=int(sec['Y'])*16+int(index)//256;z=cz*16+(int(index)//16)%16
     zones=[d for d in spec['deposits'] if name=='overworld' and contains(d['bounds'],x,y,z)]
     if not zones:results['ore-outside-deposits']+=1
     for d in zones:results['deposits'][d['id']]+=1
 for path in (dimension/'entities').glob('*.mca'):
  for doc in entity_chunks(path):scan_tags(doc,results)
 needed={(x,z) for x in (range(128) if name=='overworld' else range(-12,12)) for z in (range(128) if name=='overworld' else range(-12,12))}
 results['full-chunks']=len(full);results['missing-required-full-chunks']=len(needed-full)
 assert not needed-full, f'{name}: incomplete playable coverage'
 assert results['items']['minecraft:end_portal_frame']==0,'Creative authoring kit must be cleared'
 assert results['ore-outside-deposits']==0,f'{name}: unauthorized ore'
 assert not any(v for k,v in results['items'].items() if (k=='minecraft:diamond' and not args.expected_mined) or k.startswith('minecraft:diamond_') or k=='minecraft:deepslate_diamond_ore'),f'{name}: diamond item supply present (record explicit harvests separately)'
 report[name]=results
 print(name,json.dumps({k:v for k,v in results.items() if k!='cow-bindings'}))
assert sum(r['ore-blocks'] for r in report.values())==1536-args.expected_mined
assert sum(r['items']['minecraft:diamond'] for r in report.values())==args.expected_mined
c=sqlite3.connect(f"file:{root/'plugins/Civilizations/civilizations-v2.db'}?mode=ro",uri=True)
assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok';assert not c.execute('PRAGMA foreign_key_check').fetchall()
report['database']={'civilizations':c.execute('SELECT count(*) FROM civilizations').fetchone()[0],'claims':c.execute('SELECT count(*) FROM claims').fetchone()[0]}
if args.expected_mined:
 expected=dict(c.execute("SELECT id,entity_uuid FROM managed_mobs WHERE life='ALIVE'"))
 actual=collections.defaultdict(list)
 for r in report.values():
  for k,v in r.get('cow-bindings',{}).items():actual[k].extend(v)
 assert len(expected)==25 and len(actual)==25 and all(actual.get(k)==[v] for k,v in expected.items()),'Managed entity/SQL mismatch'
 assert c.execute('SELECT count(*) FROM managed_mob_parent_reservations').fetchone()[0]==0
 report['database']['alive']=len(expected)
 report['database']['births']=c.execute('SELECT count(*) FROM managed_mobs WHERE parent_a IS NOT NULL').fetchone()[0]
 report['database']['unresolved']=c.execute("SELECT count(*) FROM managed_mobs WHERE life NOT IN ('ALIVE','DEAD','CANCELLED')").fetchone()[0]
c.close()
report['files']={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'world').rglob('*')) if p.is_file()}
name=args.report or ('post-playtest-audit.json' if args.expected_mined else 'assembled-audit.json');assert Path(name).name==name and name.endswith('.json')
target=root/'verification'/name;assert not target.exists();target.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
print('Audit SHA256:',hashlib.sha256(target.read_bytes()).hexdigest())
