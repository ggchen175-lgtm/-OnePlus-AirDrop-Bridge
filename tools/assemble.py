"""Assemble a private local module; never publish the resulting ZIP."""
from pathlib import Path
import argparse, hashlib, json, zipfile

ROOT = Path(__file__).resolve().parent.parent

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--bridge-apk', type=Path, required=True)
    parser.add_argument('--vpn-apk', type=Path, required=True)
    parser.add_argument('--binder-so', type=Path, required=True)
    parser.add_argument('--helper-dex', type=Path, required=True)
    args = parser.parse_args()
    expected = json.loads((ROOT/'reference/proprietary-sha256.json').read_text())
    files = {}
    # Check everything before writing an output. Inputs use paths inside the original module.
    for name, digest in expected.items():
        content = (args.inputs/name).read_bytes()
        if hashlib.sha256(content).hexdigest() != digest:
            raise SystemExit('Dependency differs from baseline: ' + name)
        files[name] = content
    # Device CN feature overlay must come from the matching local environment.
    feature = (args.inputs/'cn-google-features.xml').read_bytes()
    if b'cn.google.services' in feature:
        raise SystemExit('CN feature overlay still contains cn.google.services')
    files['cn-google-features.xml'] = feature
    for path in (ROOT/'module').rglob('*'):
        if path.is_file(): files[path.relative_to(ROOT/'module').as_posix()] = path.read_bytes()
    files['README-zh.md'] = (ROOT/'docs/INSTALL.md').read_bytes()
    files['VPN-README-zh.md'] = (ROOT/'docs/INSTALL.md').read_bytes()
    files['payload/Mosey-OShare-cloud-test.apk'] = args.bridge_apk.read_bytes()
    files['payload/AirDrop-VPN-bypass-test.apk'] = args.vpn_apk.read_bytes()
    files['native/libmosey_local_binder.so'] = args.binder_so.read_bytes()
    files['support/compat-components.dex'] = args.helper_dex.read_bytes()
    for key, name in [('hook','Mosey-OShare-cloud-test.apk'), ('vpn','AirDrop-VPN-bypass-test.apk')]:
        files['payload/'+key+'.sha256'] = (hashlib.sha256(files['payload/'+name]).hexdigest()+'\n').encode()
    for name in ('files.json', 'verification.json'):
        files['payload/gms/'+name] = (ROOT/'reference'/name).read_bytes()
    files['SHA256SUMS.txt'] = ''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n'
                                    for name, data in sorted(files.items())).encode()
    out = ROOT/'dist/OnePlus-AirDrop-Bridge-0.6-local.zip'
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            info = zipfile.ZipInfo(name)
            info.create_system = 3
            mode = 0o755 if name.endswith('.sh') or name == 'native/mosey_server' else 0o644
            info.external_attr = (0o100000 | mode) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    with zipfile.ZipFile(out) as archive:
        assert archive.testzip() is None
    print(out)
    print('LOCAL ONLY: output contains proprietary dependencies. Do not upload to GitHub.')

if __name__ == '__main__': main()
