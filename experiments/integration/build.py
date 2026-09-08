"""Build the isolated Paper 26.2 fixture probe from the worktree root with Java 25."""
from pathlib import Path
import os, shutil, subprocess
assert not Path('server').is_symlink() and Path('server/S5-FIXTURE').exists(), 'S5 fixture required'
java = Path(os.environ.get('JAVA_HOME', '/opt/homebrew/opt/openjdk')) / 'bin'
classes = Path('server/verification/probe-classes')
classes.mkdir(parents=True, exist_ok=True)
shutil.copy('experiments/integration/plugin.yml', classes/'plugin.yml')
cp = os.pathsep.join(map(str, [Path('server/plugins/Civilizations.jar'), Path('server/versions/26.2/paper-26.2.jar'), *Path('server/libraries').rglob('*.jar')]))
subprocess.run([str(java/'javac'), '-cp', cp, '-d', str(classes), 'experiments/integration/IntegrationProbe.java'], check=True)
subprocess.run([str(java/'jar'), 'cf', 'server/plugins/IntegrationProbe.jar', '-C', str(classes), '.'], check=True)
