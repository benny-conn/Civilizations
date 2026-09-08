"""Remove WorldPainter's creative test kit from the STOPPED disposable S5 copy only."""
from pathlib import Path
import sys,fcntl
sys.path.insert(0,'tools/scarcity')
from prepare_world import region_chunks,write_region
import nbtlib as nbt
root=Path('server');assert not root.is_symlink() and (root/'S5-FIXTURE').exists()
with (root/'world/session.lock').open('rb') as lock:
 fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
 found=[]
 for path in sorted((root/'world/dimensions/minecraft/overworld/region').glob('*.mca')):
  chunks=list(region_chunks(path));changed=False
  for slot,doc in chunks:
   for tile in doc.get('block_entities',[]):
    items=tile.get('Items',[])
    if any(str(i.get('id',''))=='minecraft:end_portal_frame' for i in items):
     found.append([int(tile[k]) for k in ('x','y','z')]);tile['Items']=nbt.List[nbt.Compound]([]);changed=True
  if changed:write_region(path,chunks)
 print('Cleared creative authoring kit inventories:',found)
 assert len(found)==1,'Expected exactly one authoring kit'
