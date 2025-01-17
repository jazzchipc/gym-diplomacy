#!/bin/bash

# This will run the dip-q image, while mounting the current folder on the host's /tmp/dip, meaning that any change to that folder
# will be reflected on the host's folder as well.

mkdir dip-out

docker run -it \
  --rm \
  --name dip-q \
  --mount type=bind,source="$(pwd)/dip-out",target="/tmp/dip-out" \
  registry.gitlab.com/jazzchipc/dip-q:latest