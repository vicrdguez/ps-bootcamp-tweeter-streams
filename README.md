An unfinished implementation of a Tweeter score analyzer based on CoreNLP that ignores tweets overly positive or overly negative about crypto. Built during a team Bootcamp

## What does not work
The implementation of the UI and interactive query service is not finished. 

## What works
All the stream code besides what's mentioned above works.


## How to build

```bash
docker build -f services/tweep-filtering/Dockerfile    -t twitter-tweep-analyzer:latest   .
docker build -f services/score-aggregator/Dockerfile   -t twitter-score-aggregator:latest .
```
