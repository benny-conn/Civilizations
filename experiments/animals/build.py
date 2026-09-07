"""Run from repository root after ./gradlew deployTestServerPlugin and Paper bootstrap."""
from pathlib import Path
import os, shutil, subprocess
root = Path.cwd()
api = next((Path.home()/'.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/26.2.build.112-stable').rglob('*.jar'))
java = Path(os.environ.get('JAVA_HOME', '/opt/homebrew/opt/openjdk'))/'bin'
classes = root/'server/verification/animal-classes'
classes.mkdir(parents=True, exist_ok=True)
cp = os.pathsep.join(map(str, [api, root/'server/versions/26.2/paper-26.2.jar', *(root/'server/libraries').rglob('*.jar')]))
subprocess.run([str(java/'javac'), '-cp', cp, '-d', str(classes), 'experiments/animals/AnimalProbe.java'], check=True)
shutil.copy('experiments/animals/plugin.yml', classes/'plugin.yml')
subprocess.run([str(java/'jar'), 'cf', 'server/plugins/AnimalProbe.jar', '-C', str(classes), '.'], check=True)
