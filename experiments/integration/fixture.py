"""Prepare S5 only in its own ignored server directory. Never run from the Desktop symlink."""
from pathlib import Path
import shutil,subprocess,os,json,hashlib
root=Path('server')
if root.is_symlink() or root.exists(): raise SystemExit('Use a fresh worktree with no server directory')
src=Path('../civilizations-s4/server');prepared=src/'verification/prepared-2048'
audit=json.loads((src/'verification/prepared-audit.json').read_text())
actual={str(p.relative_to(prepared)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(prepared.rglob('*')) if p.is_file()}
assert actual==audit['source']['files'],'Prepared artifact differs from S4 audit'
root.mkdir();(root/'verification').mkdir()
for n in ['paper.jar','cache','libraries','versions']:
 p=src/n;t=root/n
 if p.is_dir():shutil.copytree(p,t)
 else:shutil.copy2(p,t)
shutil.copytree(prepared,root/'world')
for n in ['prototype-deposits.json','prepared-audit.json']:
 shutil.copy2(src/'verification'/n,root/'verification'/n)
(root/'S5-FIXTURE').write_text('Isolated integration; do not promote mutated world to release\n')
(root/'eula.txt').write_text('eula=true\n')
(root/'server.properties').write_text('''server-ip=127.0.0.1
server-port=25583
online-mode=true
white-list=true
enforce-whitelist=true
view-distance=2
simulation-distance=2
spawn-protection=0
pause-when-empty-seconds=0
''')
(root/'bukkit.yml').write_text('''settings:
  allow-end: false
worlds:
  world_nether:
    generator: TransitGenerator
''')
(root/'config').mkdir()
shutil.copy2(src/'config/paper-world-defaults.yml',root/'config/paper-world-defaults.yml')
(root/'plugins/Civilizations').mkdir(parents=True)
(root/'plugins/Civilizations/config.yml').write_text('''scarcity:
  cattle:
    maturity-seconds: 8
    breeding-cooldown-seconds: 4
economy:
  opening-civilization-balance: "100000.00"
''')
java=Path('/opt/homebrew/opt/openjdk/bin');classes=root/'verification/generator-classes';classes.mkdir()
(classes/'plugin.yml').write_text('name: TransitGenerator\nversion: "1"\nmain: TransitGenerator\napi-version: "26.2"\nload: STARTUP\n')
cp=os.pathsep.join(map(str,(root/'libraries').rglob('*.jar')))
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(classes),'experiments/integration/TransitGenerator.java'],check=True)
subprocess.run([str(java/'jar'),'cf',str(root/'plugins/TransitGenerator.jar'),'-C',str(classes),'.'],check=True)
print('S5 prepared; loopback 25583, no player whitelist, shortened cattle policy, End closed')
