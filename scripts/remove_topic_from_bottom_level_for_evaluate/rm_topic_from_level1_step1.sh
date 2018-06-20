mark=$1
input=${mark}.nodes.json
output=${mark}_wolevel1.nodes.json

# output can be used for evaluating compactness
grep -v "\"level\": 1" ${input} > ${output}
