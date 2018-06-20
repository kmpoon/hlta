import sys
import json
import os

def is_empty(child_value):
    empty = True
    for v in child_value:
        if 0 != len(v.keys()):
            empty = False
    return empty

def rm_empty_child(item):
    if not 'children' in item:
        return item
    value = item['children']
    if is_empty(value):
        del item['children']
        return item
    else:
        new_value = []
        for v in value:
            if 0 != len(v.keys()):
                new_value.append(rm_empty_child(v))
        item['children'] = new_value
        return item

fin = open(sys.argv[1])
data = json.load(fin)
output = []
for item in data:
    print >> sys.stderr, "item:", item
    out_item = rm_empty_child(item)
    print >> sys.stderr, "out_item:", out_item
    output.append(out_item)
print json.dumps(output)
