mark=$1
input=${mark}_wolevel1.nodes.json
output=${mark}_wolevel1_2.nodes.json

# input is from step1, which can be used for evaluating compactness
# output can be used for evaluating coherence
python rm_empty_children.py ${input} > ${output} 2> err
