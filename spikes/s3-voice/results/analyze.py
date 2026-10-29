import csv, sys, glob, os, math, statistics
from collections import defaultdict
d = sys.argv[1]
files = sorted(glob.glob(os.path.join(d, "s3_*.csv")))
def med(v):
    s=sorted(v); n=len(s); m=n//2
    return s[m] if n%2 else (s[m-1]+s[m])/2
def p95(v):
    s=sorted(v); return s[max(0,min(len(s)-1, math.ceil(0.95*len(s))-1))]
runs=[]
for f in files:
    rows=list(csv.DictReader(open(f)))
    name=os.path.basename(f)
    start=next(r for r in rows if r['kind']=='start')
    stop=[r for r in rows if r['kind']=='stop']
    items=[r for r in rows if r['kind']=='item']
    load=[r for r in rows if r['kind']=='load']
    kinds=set(r['kind'] for r in rows)
    t0=int(rows[0]['epoch_ms']); t1=int(rows[-1]['epoch_ms'])
    pss=max(int(r['pss_peak_kb']) for r in rows if r['pss_peak_kb'])
    hwm=max(int(r['vmhwm_kb']) for r in rows if r['vmhwm_kb'])
    th=[int(r['thermal']) for r in rows if r['thermal']]
    bat=[int(r['battery_pct']) for r in rows if r['battery_pct']]
    chg=set(r['charging'] for r in rows)
    # filename threads vs logged
    fthr=name.rsplit('_t',1)[1].split('.')[0]
    runs.append(dict(name=name, task=start['task'], model=start['model'], thr=start['threads'], fthr=fthr,
        complete=bool(stop), kinds=sorted(kinds), n=len(items), rtfs=[float(r['rtf']) for r in items],
        items=items, load_ms=float(load[0]['compute_ms']) if load else None, pss=pss, hwm=hwm,
        th=(min(th),max(th)), bat=(min(bat),max(bat)), chg=chg, dur=(t1-t0)/1000, detail=start['detail'],
        t0=t0, t1=t1, rows=rows, device=start['device']))
print("== per run")
for r in runs:
    print(f"{r['name']}: thr={r['thr']} (file t{r['fthr']}) complete={r['complete']} items={r['n']} dur={r['dur']:.0f}s "
          f"load={r['load_ms']/1000:.2f}s pssMax={r['pss']/1024:.1f}MiB hwm={r['hwm']/1024:.1f}MiB thermal={r['th']} bat={r['bat']} chg={r['chg']} kinds={r['kinds']} start='{r['detail']}'")
print("\n== gaps between runs (end of previous -> start of next), s")
for a,b in zip(runs,runs[1:]):
    print(f"{a['name'][3:18]} -> {b['name'][3:18]}: {(b['t0']-a['t1'])/1000:.0f}")
def summarize(sel, label):
    g=defaultdict(list)
    for r in sel: g[(r['task'],r['model'],r['thr'])].append(r)
    print(f"\n== summary: {label}")
    for k,rs in sorted(g.items()):
        rt=[x for r in rs for x in r['rtfs']]
        pss=max(r['pss'] for r in rs)/1024; hwm=max(r['hwm'] for r in rs)/1024
        loads=[r['load_ms']/1000 for r in rs if r['load_ms']]
        gate=1.0 if k[0]=='tts' else 0.7
        unf=sum(1 for r in rs if not r['complete'])
        v=[]
        if med(rt)>gate: v.append('FAIL median RTF')
        if k[0]=='stt' and pss>600: v.append('FAIL PSS>600')
        if not v and unf: v.append('INCOMPLETE')
        if not v and p95(rt)>gate: v.append('BORDERLINE p95')
        if not v and k[0]=='stt' and hwm>600: v.append('BORDERLINE RSS')
        if not v: v.append('PASS')
        print(f"{k}: runs={len(rs)} unfinished={unf} items={len(rt)} medianRTF={med(rt):.3f} p95={p95(rt):.3f} min={min(rt):.3f} max={max(rt):.3f} "
              f"pssPeak={pss:.1f}MiB ({pss*1.048576:.0f} MB) rssPeak={hwm:.1f}MiB load_med={med(loads):.2f}s -> {v}")
        per=defaultdict(list)
        for r in rs:
            for it in r['items']: per[it['item']].append(float(it['rtf']))
        print("    per item median RTF: "+", ".join(f"{i}={med(v):.3f}(n={len(v)})" for i,v in per.items()))
        if k[0]=='tts':
            per=defaultdict(list)
            for r in rs:
                for it in r['items']: per[it['item']].append((float(it['compute_ms'])/1000, float(it['audio_s'])))
            print("    per item median compute s / audio s: "+", ".join(f"{i}={med([a for a,_ in v]):.1f}/{med([b for _,b in v]):.2f}" for i,v in per.items()))
summarize(runs, "ALL files (should reproduce app Summary.kt)")
summarize([r for r in runs if r['complete']], "COMPLETE runs only")
summarize([r for r in runs if not r['complete']], "INCOMPLETE runs only (for reference)")
print("\n== STT clips and transcripts")
for r in runs:
    if r['task']!='stt': continue
    seen={}
    for it in r['items']:
        seen.setdefault(it['item'],set()).add((it['audio_s'],it['detail'],it['reference']))
    print(r['name'])
    for c,s in seen.items():
        for a,t,ref in s: print(f"   {c} {a}s distinct_transcripts={len(s)} | HYP: {t} | REF: {ref}")
