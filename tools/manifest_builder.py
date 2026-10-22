from pathlib import Path
import struct, zipfile

P=Path(__file__).resolve().parent
strings=[]
def si(s):
    if s not in strings: strings.append(s)
    return strings.index(s)
uri='http://schemas.android.com/apk/res/android'
ids={'name':0x01010003,'value':0x01010024,'label':0x01010001,
     'allowBackup':0x01010280,'hasCode':0x0101000c,'debuggable':0x0101000f,
     'versionCode':0x0101021b,'versionName':0x0101021c,
     'minSdkVersion':0x0101020c,'targetSdkVersion':0x01010270}
for n in ids: si(n)
def chunk(t,h,b): return struct.pack('<HHI',t,h,8+len(b))+b
def node(t,b): return chunk(t,16,struct.pack('<II',1,0xffffffff)+b)
def attr(name,value,typ=3,android=True):
    ni=si(name); ns=si(uri) if android else 0xffffffff
    data=si(value) if typ==3 else value
    return struct.pack('<IIIHBBI',ns,ni,data if typ==3 else 0xffffffff,8,0,typ,data)
def start(name,attrs):
    return node(0x102,struct.pack('<IIHHHHHH',0xffffffff,si(name),20,20,len(attrs),0,0,0)+b''.join(attrs))
def end(name): return node(0x103,struct.pack('<II',0xffffffff,si(name)))
nodes=[]
nodes.append(node(0x100,struct.pack('<II',si('android'),si(uri))))
nodes.append(start('manifest',[attr('package','dev.codex.moseycompat',android=False),attr('versionCode',1,0x10),attr('versionName','0.1')]))
nodes.append(start('uses-sdk',[attr('minSdkVersion',36,0x10),attr('targetSdkVersion',36,0x10)]))
nodes.append(end('uses-sdk'))
nodes.append(start('application',[attr('label','Mosey compatibility test'),attr('hasCode',0xffffffff,0x12),attr('allowBackup',0,0x12)]))
for name,val,typ in [('xposedmodule',0xffffffff,0x12),('xposeddescription','OnePlus Mosey local Binder lookup only',3),('xposedminversion',82,0x10)]:
    nodes.append(start('meta-data',[attr('name',name),attr('value',val,typ)])); nodes.append(end('meta-data'))
nodes.append(end('application')); nodes.append(end('manifest'))
nodes.append(node(0x101,struct.pack('<II',si('android'),si(uri))))
def length(n):
    return bytes([n]) if n<128 else bytes([(n>>8)|128,n&255])
raw=b''; offsets=[]
for s in strings:
    b=s.encode(); offsets.append(len(raw)); raw+=length(len(s))+length(len(b))+b+b'\0'
raw+=b'\0'*((-len(raw))%4)
pool=chunk(1,28,struct.pack('<IIIII',len(strings),0,0x100,28+4*len(strings),0)+struct.pack('<'+'I'*len(offsets),*offsets)+raw)
res=chunk(0x180,8,struct.pack('<'+'I'*len(strings),*(ids.get(s,0) for s in strings)))
body=pool+res+b''.join(nodes)
xml=chunk(3,8,body)
