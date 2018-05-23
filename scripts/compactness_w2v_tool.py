import sys

import compactness_w2v

model_file=sys.argv[1]
topic_file=sys.argv[2]

compactness_w2v.compactness_score(model_file, topic_file)
