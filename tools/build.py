"""Build unsigned bridge/VPN APKs and helper DEX with locally supplied SDK tools."""
from pathlib import Path
import argparse, base64, subprocess, zipfile
import manifest_builder

ROOT = Path(__file__).resolve().parent.parent

def run(command):
    result = subprocess.run([str(x) for x in command], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout

def dex(java, android, d8, xposed, sources, name):
    classes = ROOT / 'build' / name
    classes.mkdir(parents=True, exist_ok=True)
    output = run([java, ROOT/'tools/MemoryCompile.java', ','.join(map(str, sources)), android, xposed])
    paths = []
    for line in output.splitlines():
        classname, data = line.split(' ', 1)
        target = classes / (classname.replace('.', '/') + '.class')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(base64.b64decode(data))
        paths.append(str(target))
    result = run([java, '--class-path', d8, ROOT/'tools/MemoryDex.java', ','.join(paths), android])
    return base64.b64decode(result.strip(), validate=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java', help='JDK 17 java executable')
    parser.add_argument('--android-jar', required=True)
    parser.add_argument('--d8-jar', required=True)
    parser.add_argument('--xposed-api', required=True)
    parser.add_argument('--jp2-so', type=Path, help='Optional arm64 JP2ForAndroid 1.0.4 JNI library')
    args = parser.parse_args()
    dist = ROOT/'dist'
    dist.mkdir(exist_ok=True)
    for kind in ('bridge', 'vpn'):
        sources = sorted((ROOT/kind/'src').rglob('*.java'))
        data = dex(args.java, args.android_jar, args.d8_jar, args.xposed_api, sources, kind)
        manifest = manifest_builder.xml
        if kind == 'vpn':
            # Rebuild the binary manifest's string pool rather than modifying binary bytes.
            script = (ROOT/'tools/manifest_builder.py').read_text(encoding='utf-8')
            script = script.replace("'dev.codex.moseycompat'", "'dev.codex.moseyvpn'")
            script = script.replace("'Mosey compatibility test'", "'AirDrop VPN bypass'")
            namespace = {'__file__':str(ROOT/'tools/manifest_builder.py')}
            exec(compile(script, 'vpn-manifest', 'exec'), namespace)
            manifest = namespace['xml']
        target = dist / (kind + '-unsigned.apk')
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('AndroidManifest.xml', manifest)
            archive.writestr('classes.dex', data)
            entry = 'dev.codex.moseycompat.MoseyHook' if kind == 'bridge' else 'dev.codex.moseyvpn.MoseyVpnBypass'
            archive.writestr('assets/xposed_init', entry + '\n')
            if kind == 'bridge' and args.jp2_so:
                archive.write(args.jp2_so, 'lib/arm64-v8a/libopenjpeg.so')
                for license_file in (ROOT/'licenses').glob('*.txt'):
                    archive.write(license_file, 'assets/' + license_file.name)
        print(target.name)
    helper = dex(args.java, args.android_jar, args.d8_jar, args.xposed_api,
                 [ROOT/'support/CompatComponents.java'], 'support')
    (dist/'compat-components.dex').write_bytes(helper)
    print('compat-components.dex')
    if not args.jp2_so:
        print('No JNI decoder supplied: JPEG-2000 preview decoding will be unavailable.')

if __name__ == '__main__': main()
