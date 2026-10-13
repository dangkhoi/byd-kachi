import sys, re, html
# usage: mkprefs.py base.xml out.xml grid lang slot0 slot1 ... slot5
base, out, grid, lang = sys.argv[1:5]
slots = (sys.argv[5:] + [""]*6)[:6]
x = open(base, encoding="utf-8").read()
def setkey(x, name, val):
    pat = re.compile(r'<string name="%s">.*?</string>' % re.escape(name), re.S)
    new = '<string name="%s">%s</string>' % (name, html.escape(val, quote=False))
    if pat.search(x): return pat.sub(lambda m: new, x)
    return x.replace("</map>", "    %s\n</map>" % new)
x = setkey(x, "QA2__grid_layout", grid)
x = setkey(x, "QA2__lang", lang)
for i, s in enumerate(slots): x = setkey(x, "QA2__slot_%d" % i, ("widget:" + s) if s else "")
open(out, "w", encoding="utf-8").write(x)
