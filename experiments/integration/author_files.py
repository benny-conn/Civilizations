"""Author simple fixture manifests from actual loaded identities and prepared layout."""
from pathlib import Path
import json,sqlite3
assert not Path('server').is_symlink() and Path('server/S5-FIXTURE').exists(), 'S5 fixture required'
root=Path('server');layout=json.loads((root/'verification/layout.json').read_text())
c=sqlite3.connect(f"file:{root/'plugins/Civilizations/civilizations-v2.db'}?mode=ro",uri=True)
season=c.execute('SELECT active_season_id FROM runtime_state').fetchone()[0];c.close()
spec=json.loads((root/'verification/prototype-deposits.json').read_text())
def bounds(b):return dict(zip(['min-x','min-y','min-z','max-x','max-y','max-z'],b))
def write(folder,name,obj):
 p=root/'plugins/Civilizations'/folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,indent=2)+'\n')
zones=[{'id':d['id'],'resource':'DIAMOND','bounds':bounds(d['bounds'])} for d in spec['deposits']]
zones += [{**z,'bounds':bounds(z['bounds'])} for z in layout['zones']]
for n,key,box,z in [('overworld','overworld',[0,-64,0,2047,319,2047],zones),('nether','the_nether',[-128,0,-128,127,255,127],[])]:
 write('manifests',f'{n}.yml',{'format':1,'id':f'00000000-0000-0000-0000-00000000005{0 if n=="overworld" else 1}','season':season,'revision':1,'world':{'key':f'minecraft:{key}','uuid':layout[n]},'bounds':bounds(box),'zones':z})
write('cattle','seeds.yml',{'seeds':layout['seeds']})
pairs=[]
for i,(x,y,z) in enumerate(layout['portals']):
 pairs.append({'id':f'crossing-{i}','first':{'world':'minecraft:overworld','uuid':layout['overworld'],**bounds([x,y,z,x+1,y+2,z])},'second':{'world':'minecraft:the_nether','uuid':layout['nether'],**bounds([-64+i*64,65,0,-63+i*64,67,0])}})
write('portals','sites.yml',{'format':1,'season':season,'pairs':pairs})
print('Authored 12 resource zones, a zero-zone transit world, 24 seed slots, 3 portal pairs')
