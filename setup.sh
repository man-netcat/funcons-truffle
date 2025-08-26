#!/bin/bash

CONTAINER_NAME="funcons-truffle"
IMAGE_NAME="funcons-truffle"

# Build the image if it doesn't exist
if [[ "$(docker images -q ${IMAGE_NAME})" == "" ]]; then
    echo "Building image '${IMAGE_NAME}'..."
    docker build -t ${IMAGE_NAME} .
fi

# Create the container if it doesn't exist
if [[ "$(docker ps -a -q -f name=${CONTAINER_NAME})" == "" ]]; then
    echo "Creating container '${CONTAINER_NAME}'..."
    docker create -it --name ${CONTAINER_NAME} -v "$(pwd)":/workspace ${IMAGE_NAME} bash
fi

cat <<EOF
Inside container '${CONTAINER_NAME}':
  To run interpreter with a config:
      gradle :fctinterpreter:run --args "<../path/to/config>"
  To run generator:
      gradle :trufflegen:run
  To run tests:
      gradle :fctinterpreter:test
EOF

docker start -ai ${CONTAINER_NAME}
