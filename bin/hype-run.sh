#!/usr/bin/env bash

set -ex

MAINCLASS=com.spotify.hype.stub.ContinuationEntryPoint

STAGING_URI=${1%/}
CONTINUATION_FILE=${2}
STAGING_DIR=.tmp/$RANDOM

mkdir -p $STAGING_DIR

gsutil -m cp "$STAGING_URI/*" $STAGING_DIR

CLASSPATH="$(echo `find $STAGING_DIR -type f` | tr ' ' ':')"

exec java -cp $CLASSPATH $MAINCLASS $STAGING_DIR/$CONTINUATION_FILE
