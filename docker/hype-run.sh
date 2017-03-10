#!/usr/bin/env bash

set -ex

MAINCLASS=com.spotify.hype.stub.ContinuationEntryPoint

STAGING_URI=${1%/}
CONTINUATION_FILE=${2}
STAGING_DIR=.tmp/$RANDOM

GOOGLE_APPLICATION_CREDENTIALS=/etc/gcloud/key.json

/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file $GOOGLE_APPLICATION_CREDENTIALS

mkdir -p $STAGING_DIR

/google-cloud-sdk/bin/gsutil -m cp "$STAGING_URI/*" $STAGING_DIR

CLASSPATH="$(echo `find $STAGING_DIR -type f` | tr ' ' ':')"

exec java -cp $CLASSPATH $MAINCLASS $STAGING_DIR/$CONTINUATION_FILE
